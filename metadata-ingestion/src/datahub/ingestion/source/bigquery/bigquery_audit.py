import json
import logging
import re
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

from dateutil import parser

from datahub.emitter.mce_builder import make_dataset_urn

# ProtobufEntry is generated dynamically using a namedtuple, so mypy
# can't really deal with it. As such, we short circuit mypy's typing
# but keep the code relatively clear by retaining dummy types.
#
# from google.cloud.logging_v2 import ProtobufEntry
# AuditLogEntry = ProtobufEntry
from datahub.utilities.parsing_util import (
    get_first_missing_key,
    get_first_missing_key_any,
)

# Handle table snapshots
# See https://cloud.google.com/bigquery/docs/table-snapshots-intro.
SNAPSHOT_TABLE_REGEX = re.compile(r"^(.+)@(\d{13})$")
PARTITION_SUMMARY_REGEXP = re.compile(r"^(.+)\$__PARTITIONS_SUMMARY__$")

BQ_DATETIME_FORMAT = "%Y-%m-%dT%H:%M:%SZ"
BQ_DATE_SHARD_FORMAT = "%Y%m%d"

BQ_AUDIT_V2 = {
    "BQ_FILTER_REGEX_ALLOW_TEMPLATE": """protoPayload.metadata.jobChange.job.jobStats.queryStats.referencedTables =~ "projects/.*/datasets/.*/tables/{table_allow_pattern}"
""".strip(
        "\t \n"
    ),
    "BQ_FILTER_REGEX_DENY_TEMPLATE": """
    {logical_operator}
            NOT (
                protoPayload.metadata.jobChange.job.jobStats.queryStats.referencedTables =~ "projects/.*/datasets/.*/tables/{table_deny_pattern}"
            )
""".strip(
        "\t \n"
    ),
    "BQ_FILTER_RULE_TEMPLATE": """
resource.type=("bigquery_project" OR "bigquery_dataset")
AND
(
    (
        protoPayload.methodName=
            (
                "google.cloud.bigquery.v2.JobService.Query"
                OR
                "google.cloud.bigquery.v2.JobService.InsertJob"
            )
        AND
        protoPayload.metadata.jobChange.job.jobStatus.jobState="DONE"
        AND NOT protoPayload.metadata.jobChange.job.jobStatus.errorResult:*
        AND protoPayload.metadata.jobChange.job.jobStats.queryStats.referencedTables:*
        AND NOT protoPayload.metadata.jobChange.job.jobStats.queryStats.referencedTables =~ "projects/.*/datasets/.*/tables/__TABLES__|__TABLES_SUMMARY__|INFORMATION_SCHEMA.*"
         AND (
            {allow_regex}
            {deny_regex}
                OR
            protoPayload.metadata.tableDataRead.reason = "JOB"
        )
    )
    OR
    (
        protoPayload.metadata.tableDataRead:*
    )
)
AND
timestamp >= "{start_time}"
AND
timestamp < "{end_time}"
""".strip(
        "\t \n"
    ),
}

DEBUG_INCLUDE_FULL_PAYLOADS = False

AuditLogEntry = Any

# BigQueryAuditMetadata is the v2 format in which audit logs are exported to BigQuery
BigQueryAuditMetadata = Any

logger: logging.Logger = logging.getLogger(__name__)


@dataclass(frozen=True, order=True)
class BigqueryTableIdentifier:
    project_id: str
    dataset: str
    table: str

    _BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX: str = "((.+)[_$])?(\\d{4,10})$"

    def _get_table_and_shard(self, table_name: str) -> Tuple[str, Optional[str]]:
        match = re.search(
            BigqueryTableIdentifier._BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX,
            table_name,
            re.IGNORECASE,
        )
        if match:
            table_name = match.group(2)
            shard = match.group(3)
            return table_name, shard
        return table_name, None

    @classmethod
    def from_string_name(cls, table: str) -> "BigqueryTableIdentifier":
        parts = table.split(".")
        return cls(parts[0], parts[1], parts[2])

    def raw_table(self):
        return f"{self.project_id}.{self.dataset}.{self.table}"

    @staticmethod
    def _remove_suffix(input_string, suffix):
        if suffix and input_string.endswith(suffix):
            return input_string[: -len(suffix)]
        return input_string

    def get_table(self) -> str:
        shortened_table_name = self.table
        # if table name ends in _* or * then we strip it as that represents a query on a sharded table
        shortened_table_name = self._remove_suffix(shortened_table_name, "_*")
        shortened_table_name = self._remove_suffix(shortened_table_name, "*")

        table_name, _ = self._get_table_and_shard(shortened_table_name)
        if not table_name:
            table_name = self.dataset

        matches = SNAPSHOT_TABLE_REGEX.match(table_name)
        if matches:
            table_name = matches.group(1)
            logger.debug(f"Found table snapshot. Using {table_name} as the table name.")

        # Handle exceptions
        invalid_chars_in_table_name: List[str] = [
            c for c in {"$", "@"} if c in table_name
        ]
        if invalid_chars_in_table_name:
            raise ValueError(
                f"Cannot handle {self} - poorly formatted table name, contains {invalid_chars_in_table_name}"
            )

        return f"{self.project_id}.{self.dataset}.{table_name}"

    def __str__(self) -> str:
        return self.get_table()


@dataclass(frozen=True, order=True)
class BigQueryTableRef:
    table_identifier: BigqueryTableIdentifier

    @classmethod
    def from_bigquery_table(cls, table: BigqueryTableIdentifier) -> "BigQueryTableRef":
        return cls(
            BigqueryTableIdentifier(table.project_id, table.dataset, table.table)
        )

    @classmethod
    def from_spec_obj(cls, spec: dict) -> "BigQueryTableRef":
        return cls(
            BigqueryTableIdentifier(
                spec["projectId"], spec["datasetId"], spec["tableId"]
            )
        )

    @classmethod
    def from_string_name(cls, ref: str) -> "BigQueryTableRef":
        parts = ref.split("/")
        if parts[0] != "projects" or parts[2] != "datasets" or parts[4] != "tables":
            raise ValueError(f"invalid BigQuery table reference: {ref}")
        return cls(BigqueryTableIdentifier(parts[1], parts[3], parts[5]))

    def is_temporary_table(self, prefix: str) -> bool:
        # Temporary tables will have a dataset that begins with an underscore.
        return self.table_identifier.dataset.startswith(prefix)

    def get_sanitized_name(self) -> "BigQueryTableRef":
        sanitized_table = self.table_identifier.get_table()
        # Handle partitioned and sharded tables.
        return BigQueryTableRef(
            BigqueryTableIdentifier.from_string_name(sanitized_table)
        )

    def __str__(self) -> str:
        return f"projects/{self.table_identifier.project_id}/datasets/{self.table_identifier.dataset}/tables/{self.table_identifier.table}"


def _table_ref_to_urn(ref: BigQueryTableRef, env: str) -> str:
    return make_dataset_urn(
        "bigquery",
        f"{ref.table_identifier.project_id}.{ref.table_identifier.dataset}.{ref.table_identifier.table}",
        env,
    )


@dataclass
class QueryEvent:
    """
    A container class for a query job completion event.
    See https://cloud.google.com/bigquery/docs/reference/auditlogs/rest/Shared.Types/AuditData#JobCompletedEvent.
    """

    timestamp: datetime
    actor_email: str
    query: str
    statementType: str

    job_name: Optional[str] = None
    destinationTable: Optional[BigQueryTableRef] = None
    referencedTables: List[BigQueryTableRef] = field(default_factory=list)
    referencedViews: List[BigQueryTableRef] = field(default_factory=list)
    payload: Optional[Dict] = None
    stats: Optional[Dict] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    billed_bytes: Optional[int] = None
    default_dataset: Optional[str] = None
    numAffectedRows: Optional[int] = None

    @staticmethod
    def get_missing_key_entry(entry: AuditLogEntry) -> Optional[str]:
        return get_first_missing_key(
            inp_dict=entry.payload, keys=["serviceData", "jobCompletedEvent", "job"]
        )

    @staticmethod
    def get_missing_key_entry_v2(entry: AuditLogEntry) -> Optional[str]:
        return get_first_missing_key(
            inp_dict=entry.payload, keys=["metadata", "jobChange", "job"]
        )

    @staticmethod
    def _job_name_ref(project: str, jobId: str) -> Optional[str]:
        if project and jobId:
            return f"projects/{project}/jobs/{jobId}"
        return None

    @classmethod
    def from_entry(cls, entry: AuditLogEntry) -> "QueryEvent":
        job: Dict = entry.payload["serviceData"]["jobCompletedEvent"]["job"]
        job_query_conf: Dict = job["jobConfiguration"]["query"]
        # basic query_event
        query_event = QueryEvent(
            timestamp=entry.timestamp,
            actor_email=entry.payload["authenticationInfo"]["principalEmail"],
            query=job_query_conf["query"],
            job_name=QueryEvent._job_name_ref(
                job.get("jobName", {}).get("projectId"),
                job.get("jobName", {}).get("jobId"),
            ),
            default_dataset=job_query_conf["defaultDataset"]
            if job_query_conf["defaultDataset"]
            else None,
            start_time=parser.parse(job["jobStatistics"]["startTime"])
            if job["jobStatistics"]["startTime"]
            else None,
            end_time=parser.parse(job["jobStatistics"]["endTime"])
            if job["jobStatistics"]["endTime"]
            else None,
            numAffectedRows=int(job["jobStatistics"]["queryOutputRowCount"])
            if "queryOutputRowCount" in job["jobStatistics"]
            and job["jobStatistics"]["queryOutputRowCount"]
            else None,
            statementType=job_query_conf.get("statementType", "UNKNOWN"),
        )
        # destinationTable
        raw_dest_table = job_query_conf.get("destinationTable")
        if raw_dest_table:
            query_event.destinationTable = BigQueryTableRef.from_spec_obj(
                raw_dest_table
            )
        # statementType
        # referencedTables
        job_stats: Dict = job["jobStatistics"]
        if job_stats.get("totalBilledBytes"):
            query_event.billed_bytes = job_stats["totalBilledBytes"]

        raw_ref_tables = job_stats.get("referencedTables")
        if raw_ref_tables:
            query_event.referencedTables = [
                BigQueryTableRef.from_spec_obj(spec) for spec in raw_ref_tables
            ]
        # referencedViews
        raw_ref_views = job_stats.get("referencedViews")
        if raw_ref_views:
            query_event.referencedViews = [
                BigQueryTableRef.from_spec_obj(spec) for spec in raw_ref_views
            ]
        # payload
        query_event.payload = entry.payload if DEBUG_INCLUDE_FULL_PAYLOADS else None
        if not query_event.job_name:
            logger.debug(
                "jobName from query events is absent. "
                "Auditlog entry - {logEntry}".format(logEntry=entry)
            )

        return query_event

    @staticmethod
    def get_missing_key_exported_bigquery_audit_metadata(
        row: BigQueryAuditMetadata,
    ) -> Optional[str]:
        missing_key = get_first_missing_key_any(
            row._xxx_field_to_index, ["timestamp", "protoPayload", "metadata"]
        )
        if not missing_key:
            missing_key = get_first_missing_key_any(
                json.loads(row["metadata"]), ["jobChange"]
            )
        return missing_key

    @classmethod
    def from_exported_bigquery_audit_metadata(
        cls, row: BigQueryAuditMetadata
    ) -> "QueryEvent":

        payload: Dict = row["protoPayload"]
        metadata: Dict = json.loads(row["metadata"])
        job: Dict = metadata["jobChange"]["job"]
        query_config: Dict = job["jobConfig"]["queryConfig"]
        query_stats: Dict = job["jobStats"]["queryStats"]

        # basic query_event
        query_event = QueryEvent(
            timestamp=row["timestamp"],
            actor_email=payload["authenticationInfo"]["principalEmail"],
            query=query_config["query"],
            job_name=job["jobName"],
            default_dataset=query_config["defaultDataset"]
            if query_config.get("defaultDataset")
            else None,
            start_time=parser.parse(job["jobStats"]["startTime"])
            if job["jobStats"]["startTime"]
            else None,
            end_time=parser.parse(job["jobStats"]["endTime"])
            if job["jobStats"]["endTime"]
            else None,
            numAffectedRows=int(query_stats["outputRowCount"])
            if query_stats.get("outputRowCount")
            else None,
            statementType=query_config.get("statementType", "UNKNOWN"),
        )
        # jobName
        query_event.job_name = job.get("jobName")
        # destinationTable
        raw_dest_table = query_config.get("destinationTable")
        if raw_dest_table:
            query_event.destinationTable = BigQueryTableRef.from_string_name(
                raw_dest_table
            )
        # referencedTables
        raw_ref_tables = query_stats.get("referencedTables")
        if raw_ref_tables:
            query_event.referencedTables = [
                BigQueryTableRef.from_string_name(spec) for spec in raw_ref_tables
            ]
        # referencedViews
        raw_ref_views = query_stats.get("referencedViews")
        if raw_ref_views:
            query_event.referencedViews = [
                BigQueryTableRef.from_string_name(spec) for spec in raw_ref_views
            ]
        # payload
        query_event.payload = payload if DEBUG_INCLUDE_FULL_PAYLOADS else None

        if not query_event.job_name:
            logger.debug(
                "jobName from query events is absent. "
                "BigQueryAuditMetadata entry - {logEntry}".format(logEntry=row)
            )

        if query_stats.get("totalBilledBytes"):
            query_event.billed_bytes = int(query_stats["totalBilledBytes"])

        return query_event

    @classmethod
    def from_entry_v2(cls, row: BigQueryAuditMetadata) -> "QueryEvent":
        payload: Dict = row.payload
        metadata: Dict = payload["metadata"]
        job: Dict = metadata["jobChange"]["job"]
        query_config: Dict = job["jobConfig"]["queryConfig"]
        query_stats: Dict = job["jobStats"]["queryStats"]

        # basic query_event
        query_event = QueryEvent(
            job_name=job["jobName"],
            timestamp=row.timestamp,
            actor_email=payload["authenticationInfo"]["principalEmail"],
            query=query_config["query"],
            default_dataset=query_config["defaultDataset"]
            if "defaultDataset" in query_config and query_config["defaultDataset"]
            else None,
            start_time=parser.parse(job["jobStats"]["startTime"])
            if job["jobStats"]["startTime"]
            else None,
            end_time=parser.parse(job["jobStats"]["endTime"])
            if job["jobStats"]["endTime"]
            else None,
            numAffectedRows=int(query_stats["outputRowCount"])
            if "outputRowCount" in query_stats and query_stats["outputRowCount"]
            else None,
            statementType=query_config.get("statementType", "UNKNOWN"),
        )
        query_event.job_name = job.get("jobName")
        # destinationTable
        raw_dest_table = query_config.get("destinationTable")
        if raw_dest_table:
            query_event.destinationTable = BigQueryTableRef.from_string_name(
                raw_dest_table
            )
        # statementType
        # referencedTables
        raw_ref_tables = query_stats.get("referencedTables")
        if raw_ref_tables:
            query_event.referencedTables = [
                BigQueryTableRef.from_string_name(spec) for spec in raw_ref_tables
            ]
        # referencedViews
        raw_ref_views = query_stats.get("referencedViews")
        if raw_ref_views:
            query_event.referencedViews = [
                BigQueryTableRef.from_string_name(spec) for spec in raw_ref_views
            ]
        # payload
        query_event.payload = payload if DEBUG_INCLUDE_FULL_PAYLOADS else None

        if not query_event.job_name:
            logger.debug(
                "jobName from query events is absent. "
                "BigQueryAuditMetadata entry - {logEntry}".format(logEntry=row)
            )

        if query_stats.get("totalBilledBytes"):
            query_event.billed_bytes = int(query_stats["totalBilledBytes"])

        return query_event


@dataclass
class ReadEvent:
    """
    A container class for data from a TableDataRead event.
    See https://cloud.google.com/bigquery/docs/reference/auditlogs/rest/Shared.Types/BigQueryAuditMetadata#BigQueryAuditMetadata.TableDataRead.
    """

    timestamp: datetime
    actor_email: str

    resource: BigQueryTableRef
    fieldsRead: List[str]
    readReason: Optional[str]
    jobName: Optional[str]

    payload: Any

    # We really should use composition here since the query isn't actually
    # part of the read event, but this solution is just simpler.
    # query: Optional["QueryEvent"] = None  # populated via join

    @classmethod
    def get_missing_key_entry(cls, entry: AuditLogEntry) -> Optional[str]:
        return (
            get_first_missing_key(
                inp_dict=entry.payload, keys=["metadata", "tableDataRead"]
            )
            or get_first_missing_key(
                inp_dict=entry.payload, keys=["authenticationInfo", "principalEmail"]
            )
            or get_first_missing_key(inp_dict=entry.payload, keys=["resourceName"])
        )

    @staticmethod
    def get_missing_key_exported_bigquery_audit_metadata(
        row: BigQueryAuditMetadata,
    ) -> Optional[str]:
        missing_key = get_first_missing_key_any(dict(row), ["metadata"])
        if not missing_key:
            metadata = json.loads(row["metadata"])
            missing_key = get_first_missing_key_any(metadata, ["tableDataRead"])

        return missing_key

    @classmethod
    def from_entry(cls, entry: AuditLogEntry) -> "ReadEvent":
        user = entry.payload["authenticationInfo"]["principalEmail"]
        resourceName = entry.payload["resourceName"]
        readInfo = entry.payload["metadata"]["tableDataRead"]

        fields = readInfo.get("fields", [])

        # https://cloud.google.com/bigquery/docs/reference/auditlogs/rest/Shared.Types/BigQueryAuditMetadata.TableDataRead.Reason
        readReason = readInfo.get("reason")
        jobName = None
        if readReason == "JOB":
            jobName = readInfo.get("jobName")

        readEvent = ReadEvent(
            actor_email=user,
            timestamp=entry.timestamp,
            resource=BigQueryTableRef.from_string_name(resourceName),
            fieldsRead=fields,
            readReason=readReason,
            jobName=jobName,
            payload=entry.payload if DEBUG_INCLUDE_FULL_PAYLOADS else None,
        )
        if readReason == "JOB" and not jobName:
            logger.debug(
                "jobName from read events is absent when readReason is JOB. "
                "Auditlog entry - {logEntry}".format(logEntry=entry)
            )
        return readEvent

    @classmethod
    def from_exported_bigquery_audit_metadata(
        cls, row: BigQueryAuditMetadata
    ) -> "ReadEvent":
        payload = row["protoPayload"]
        user = payload["authenticationInfo"]["principalEmail"]
        resourceName = payload["resourceName"]
        metadata = json.loads(row["metadata"])
        readInfo = metadata["tableDataRead"]

        fields = readInfo.get("fields", [])

        # https://cloud.google.com/bigquery/docs/reference/auditlogs/rest/Shared.Types/BigQueryAuditMetadata.TableDataRead.Reason
        readReason = readInfo.get("reason")
        jobName = None
        if readReason == "JOB":
            jobName = readInfo.get("jobName")

        readEvent = ReadEvent(
            actor_email=user,
            timestamp=row["timestamp"],
            resource=BigQueryTableRef.from_string_name(resourceName),
            fieldsRead=fields,
            readReason=readReason,
            jobName=jobName,
            payload=payload if DEBUG_INCLUDE_FULL_PAYLOADS else None,
        )
        if readReason == "JOB" and not jobName:
            logger.debug(
                "jobName from read events is absent when readReason is JOB. "
                "Auditlog entry - {logEntry}".format(logEntry=row)
            )
        return readEvent


@dataclass()
class AuditEvent:
    read_event: Optional[ReadEvent] = None
    query_event: Optional[QueryEvent] = None
