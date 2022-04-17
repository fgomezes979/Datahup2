"""LDAP Source"""
import dataclasses
from typing import Any, Dict, Iterable, List, Optional

import ldap
from ldap.controls import SimplePagedResultsControl

from datahub.configuration.common import ConfigModel, ConfigurationError
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.source import Source, SourceReport
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.metadata.com.linkedin.pegasus2avro.mxe import MetadataChangeEvent
from datahub.metadata.schema_classes import (
    CorpGroupInfoClass,
    CorpGroupSnapshotClass,
    CorpUserInfoClass,
    CorpUserSnapshotClass,
)

# default mapping for attrs
attrs_mapping: Dict[str, Any] = {}
attrs_mapping["sAMAccountName"] = "sAMAccountName"
attrs_mapping["uid"] = "uid"
attrs_mapping["objectClass"] = "objectClass"
attrs_mapping["manager"] = "manager"
attrs_mapping["givenName"] = "givenName"
attrs_mapping["sn"] = "sn"
attrs_mapping["cn"] = "cn"
attrs_mapping["mail"] = "mail"
attrs_mapping["displayName"] = "displayName"
attrs_mapping["departmentNumber"] = "departmentNumber"
attrs_mapping["title"] = "title"
attrs_mapping["owner"] = "owner"
attrs_mapping["managedBy"] = "managedBy"
attrs_mapping["uniqueMember"] = "uniqueMember"
attrs_mapping["member"] = "member"


def create_controls(pagesize: int) -> SimplePagedResultsControl:
    """
    Create an LDAP control with a page size of "pagesize".
    """
    return SimplePagedResultsControl(True, size=pagesize, cookie="")


def get_pctrls(
    serverctrls: List[SimplePagedResultsControl],
) -> List[SimplePagedResultsControl]:
    """
    Lookup an LDAP paged control object from the returned controls.
    """
    return [
        c for c in serverctrls if c.controlType == SimplePagedResultsControl.controlType
    ]


def set_cookie(
    lc_object: SimplePagedResultsControl,
    pctrls: List[SimplePagedResultsControl],
) -> bool:
    """
    Push latest cookie back into the page control.
    """

    cookie = pctrls[0].cookie
    lc_object.cookie = cookie
    return bool(cookie)


class LDAPSourceConfig(ConfigModel):
    """Config used by the LDAP Source."""

    # Server configuration.
    ldap_server: str
    ldap_user: str
    ldap_password: str

    # Extraction configuration.
    base_dn: str
    filter: str = "(objectClass=*)"

    # If set to true, any users without first and last names will be dropped.
    drop_missing_first_last_name: bool = True

    page_size: int = 20

    # default mapping for attrs
    attrs_mapping: Dict[str, Any] = {}


def guess_person_ldap(attrs: Dict[str, Any], config: LDAPSourceConfig) -> Optional[str]:
    """Determine the user's LDAP based on the DN and attributes."""
    if config.attrs_mapping["sAMAccountName"] in attrs:
        return attrs[config.attrs_mapping["sAMAccountName"]][0].decode()
    if config.attrs_mapping["uid"] in attrs:
        return attrs[config.attrs_mapping["uid"]][0].decode()
    return None


@dataclasses.dataclass
class LDAPSourceReport(SourceReport):
    dropped_dns: List[str] = dataclasses.field(default_factory=list)

    def report_dropped(self, dn: str) -> None:
        self.dropped_dns.append(dn)


@dataclasses.dataclass
class LDAPSource(Source):
    """LDAP Source Class."""

    config: LDAPSourceConfig
    report: LDAPSourceReport

    def __init__(self, ctx: PipelineContext, config: LDAPSourceConfig):
        """Constructor."""
        super().__init__(ctx)
        self.config = config
        # ensure prior defaults are in place
        for k in attrs_mapping:
            if k not in self.config.attrs_mapping:
                self.config.attrs_mapping[k] = attrs_mapping[k]

        self.report = LDAPSourceReport()

        ldap.set_option(ldap.OPT_X_TLS_REQUIRE_CERT, ldap.OPT_X_TLS_ALLOW)
        ldap.set_option(ldap.OPT_REFERRALS, 0)

        self.ldap_client = ldap.initialize(self.config.ldap_server)
        self.ldap_client.protocol_version = 3

        try:
            self.ldap_client.simple_bind_s(
                self.config.ldap_user, self.config.ldap_password
            )
        except ldap.LDAPError as e:
            raise ConfigurationError("LDAP connection failed") from e

        self.lc = create_controls(self.config.page_size)

    @classmethod
    def create(cls, config_dict: Dict[str, Any], ctx: PipelineContext) -> "LDAPSource":
        """Factory method."""
        config = LDAPSourceConfig.parse_obj(config_dict)
        return cls(ctx, config)

    def get_workunits(self) -> Iterable[MetadataWorkUnit]:
        """Returns an Iterable containing the workunits to ingest LDAP users or groups."""
        cookie = True
        while cookie:
            try:
                msgid = self.ldap_client.search_ext(
                    self.config.base_dn,
                    ldap.SCOPE_SUBTREE,
                    self.config.filter,
                    serverctrls=[self.lc],
                )
                _rtype, rdata, _rmsgid, serverctrls = self.ldap_client.result3(msgid)
            except ldap.LDAPError as e:
                self.report.report_failure(
                    "ldap-control", "LDAP search failed: {}".format(e)
                )
                break

            for dn, attrs in rdata:
                if dn is None:
                    continue

                if not attrs:
                    self.report.report_warning(
                        "<general>",
                        f"skipping {dn} because attrs is empty; check your permissions if this is unexpected",
                    )
                    continue

                if (
                    b"inetOrgPerson" in attrs[self.config.attrs_mapping["objectClass"]]
                    or b"posixAccount"
                    in attrs[self.config.attrs_mapping["objectClass"]]
                    or b"person" in attrs[self.config.attrs_mapping["objectClass"]]
                ):
                    yield from self.handle_user(dn, attrs)
                elif (
                    b"posixGroup" in attrs[self.config.attrs_mapping["objectClass"]]
                    or b"organizationalUnit"
                    in attrs[self.config.attrs_mapping["objectClass"]]
                    or b"group" in attrs[self.config.attrs_mapping["objectClass"]]
                ):
                    yield from self.handle_group(dn, attrs)
                else:
                    self.report.report_dropped(dn)

            pctrls = get_pctrls(serverctrls)
            if not pctrls:
                self.report.report_failure(
                    "ldap-control", "Server ignores RFC 2696 control."
                )
                break

            cookie = set_cookie(self.lc, pctrls)

    def handle_user(self, dn: str, attrs: Dict[str, Any]) -> Iterable[MetadataWorkUnit]:
        """
        Handle a DN and attributes by adding manager info and constructing a
        work unit based on the information.
        """
        manager_ldap = None
        if self.config.attrs_mapping["manager"] in attrs:
            try:
                m_cn = attrs[self.config.attrs_mapping["manager"]][0].decode()
                manager_msgid = self.ldap_client.search_ext(
                    m_cn,
                    ldap.SCOPE_BASE,
                    self.config.filter,
                    serverctrls=[self.lc],
                )
                _m_dn, m_attrs = self.ldap_client.result3(manager_msgid)[1][0]
                manager_ldap = guess_person_ldap(m_attrs, self.config)
            except ldap.LDAPError as e:
                self.report.report_warning(
                    dn, "manager LDAP search failed: {}".format(e)
                )

        mce = self.build_corp_user_mce(dn, attrs, manager_ldap)
        if mce:
            wu = MetadataWorkUnit(dn, mce)
            self.report.report_workunit(wu)
            yield wu
        else:
            self.report.report_dropped(dn)

    def handle_group(
        self, dn: str, attrs: Dict[str, Any]
    ) -> Iterable[MetadataWorkUnit]:
        """Creates a workunit for LDAP groups."""

        mce = self.build_corp_group_mce(attrs)
        if mce:
            wu = MetadataWorkUnit(dn, mce)
            self.report.report_workunit(wu)
            yield wu
        else:
            self.report.report_dropped(dn)

    def build_corp_user_mce(
        self, dn: str, attrs: dict, manager_ldap: Optional[str]
    ) -> Optional[MetadataChangeEvent]:
        """
        Create the MetadataChangeEvent via DN and attributes.
        """
        ldap_user = guess_person_ldap(attrs, self.config)

        if self.config.drop_missing_first_last_name and (
            self.config.attrs_mapping["givenName"] not in attrs
            or self.config.attrs_mapping["sn"] not in attrs
        ):
            return None
        full_name = attrs[self.config.attrs_mapping["cn"]][0].decode()
        first_name = attrs[self.config.attrs_mapping["givenName"]][0].decode()
        last_name = attrs[self.config.attrs_mapping["sn"]][0].decode()

        email = (
            (attrs[self.config.attrs_mapping["mail"]][0]).decode()
            if self.config.attrs_mapping["mail"] in attrs
            else ldap_user
        )
        display_name = (
            (attrs[self.config.attrs_mapping["displayName"]][0]).decode()
            if self.config.attrs_mapping["displayName"] in attrs
            else full_name
        )
        department = (
            (attrs[self.config.attrs_mapping["departmentNumber"]][0]).decode()
            if self.config.attrs_mapping["departmentNumber"] in attrs
            else None
        )
        title = (
            attrs[self.config.attrs_mapping["title"]][0].decode()
            if self.config.attrs_mapping["title"] in attrs
            else None
        )
        manager_urn = f"urn:li:corpuser:{manager_ldap}" if manager_ldap else None

        return MetadataChangeEvent(
            proposedSnapshot=CorpUserSnapshotClass(
                urn=f"urn:li:corpuser:{ldap_user}",
                aspects=[
                    CorpUserInfoClass(
                        active=True,
                        email=email,
                        fullName=full_name,
                        firstName=first_name,
                        lastName=last_name,
                        departmentName=department,
                        displayName=display_name,
                        title=title,
                        managerUrn=manager_urn,
                    )
                ],
            )
        )

    def build_corp_group_mce(self, attrs: dict) -> Optional[MetadataChangeEvent]:
        """Creates a MetadataChangeEvent for LDAP groups."""
        cn = attrs.get(self.config.attrs_mapping["cn"])
        if cn:
            full_name = cn[0].decode()
            owners = parse_from_attrs(attrs, self.config.attrs_mapping["owner"])
            members = parse_from_attrs(attrs, self.config.attrs_mapping["uniqueMember"])
            email = (
                attrs[self.config.attrs_mapping["mail"]][0].decode()
                if self.config.attrs_mapping["mail"] in attrs
                else full_name
            )

            return MetadataChangeEvent(
                proposedSnapshot=CorpGroupSnapshotClass(
                    urn=f"urn:li:corpGroup:{full_name}",
                    aspects=[
                        CorpGroupInfoClass(
                            email=email,
                            admins=owners,
                            members=members,
                            groups=[],
                        )
                    ],
                )
            )
        return None

    def get_report(self) -> LDAPSourceReport:
        """Returns the source report."""
        return self.report

    def close(self) -> None:
        """Closes the Source."""
        self.ldap_client.unbind()


def parse_from_attrs(attrs: Dict[str, Any], filter_key: str) -> List[str]:
    """Converts a list of LDAP formats to Datahub corpuser strings."""
    if filter_key in attrs:
        return [
            f"urn:li:corpuser:{strip_ldap_info(ldap_user)}"
            for ldap_user in attrs[filter_key]
        ]
    return []


def strip_ldap_info(input_clean: bytes) -> str:
    """Converts a b'uid=username,ou=Groups,dc=internal,dc=machines'
    format to username"""
    return input_clean.decode().split(",")[0].lstrip("uid=")
