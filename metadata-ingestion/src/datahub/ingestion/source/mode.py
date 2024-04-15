import dataclasses
import logging
import re
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from typing import Dict, Iterable, List, Optional, Set, Tuple, Union

import dateutil.parser as dp
import pydantic
import requests
import tenacity
from pydantic import Field, validator
from requests.models import HTTPBasicAuth, HTTPError
from sqllineage.runner import LineageRunner
from tenacity import retry_if_exception_type, stop_after_attempt, wait_exponential

import datahub.emitter.mce_builder as builder
from datahub.configuration.common import AllowDenyPattern, ConfigModel
from datahub.configuration.source_common import DatasetLineageProviderConfigBase
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.decorators import (
    SourceCapability,
    SupportStatus,
    capability,
    config_class,
    platform_name,
    support_status,
)
from datahub.ingestion.api.source import MetadataWorkUnitProcessor, SourceReport
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.source.state.stale_entity_removal_handler import (
    StaleEntityRemovalHandler,
    StaleEntityRemovalSourceReport,
    StatefulStaleMetadataRemovalConfig,
)
from datahub.ingestion.source.state.stateful_ingestion_base import (
    StatefulIngestionConfigBase,
    StatefulIngestionSourceBase,
)
from datahub.metadata.com.linkedin.pegasus2avro.common import (
    AuditStamp,
    ChangeAuditStamps,
)
from datahub.metadata.com.linkedin.pegasus2avro.metadata.snapshot import (
    ChartSnapshot,
    DashboardSnapshot,
)
from datahub.metadata.com.linkedin.pegasus2avro.mxe import MetadataChangeEvent
from datahub.metadata.schema_classes import (
    BrowsePathsClass,
    ChangeTypeClass,
    ChartInfoClass,
    ChartQueryClass,
    ChartQueryTypeClass,
    ChartTypeClass,
    DashboardInfoClass,
    DashboardUsageStatisticsClass,
    DatasetLineageTypeClass,
    DatasetPropertiesClass,
    EmbedClass,
    FineGrainedLineageClass,
    FineGrainedLineageDownstreamTypeClass,
    FineGrainedLineageUpstreamTypeClass,
    GlobalTagsClass,
    InputFieldClass,
    InputFieldsClass,
    OperationClass,
    OperationTypeClass,
    OtherSchemaClass,
    OwnerClass,
    OwnershipClass,
    OwnershipTypeClass,
    QueryLanguageClass,
    QueryPropertiesClass,
    QuerySourceClass,
    QueryStatementClass,
    SchemaFieldClass,
    SchemaFieldDataTypeClass,
    SchemaMetadataClass,
    StringTypeClass,
    SubTypesClass,
    TagAssociationClass,
    TagPropertiesClass,
    UpstreamClass,
    UpstreamLineageClass,
)
from datahub.metadata.urns import QueryUrn
from datahub.sql_parsing.sqlglot_lineage import (
    ColumnLineageInfo,
    SqlParsingResult,
    create_lineage_sql_parsed_result,
    infer_output_schema,
)
from datahub.utilities import config_clean
from datahub.utilities.lossy_collections import LossyList

logger: logging.Logger = logging.getLogger(__name__)


class ModeAPIConfig(ConfigModel):
    retry_backoff_multiplier: Union[int, float] = Field(
        default=2,
        description="Multiplier for exponential backoff when waiting to retry",
    )
    max_retry_interval: Union[int, float] = Field(
        default=10, description="Maximum interval to wait when retrying"
    )
    max_attempts: int = Field(
        default=5, description="Maximum number of attempts to retry before failing"
    )


class ModeConfig(StatefulIngestionConfigBase, DatasetLineageProviderConfigBase):
    # See https://mode.com/developer/api-reference/authentication/
    # for authentication
    connect_uri: str = Field(
        default="https://app.mode.com", description="Mode host URL."
    )
    token: str = Field(description="Mode user token.")
    password: pydantic.SecretStr = Field(
        description="Mode password for authentication."
    )

    workspace: Optional[str] = Field(default=None, description="")
    default_schema: str = Field(
        default="public",
        description="Default schema to use when schema is not provided in an SQL query",
    )

    space_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern(
            deny=["^Personal$"],
        ),
        description="Regex patterns for mode spaces to filter in ingestion (Spaces named as 'Personal' are filtered by default.) Specify regex to only match the space name. e.g. to only ingest space named analytics, use the regex 'analytics'",
    )

    owner_username_instead_of_email: Optional[bool] = Field(
        default=True, description="Use username for owner URN instead of Email"
    )
    api_options: ModeAPIConfig = Field(
        default=ModeAPIConfig(),
        description='Retry/Wait settings for Mode API to avoid "Too many Requests" error. See Mode API Options below',
    )

    ingest_embed_url: bool = Field(
        default=True, description="Whether to Ingest embed URL for Reports"
    )

    stateful_ingestion: Optional[StatefulStaleMetadataRemovalConfig] = None

    tag_measures_and_dimensions: Optional[bool] = Field(
        default=True, description="Tag measures and dimensions in the schema"
    )

    @validator("connect_uri")
    def remove_trailing_slash(cls, v):
        return config_clean.remove_trailing_slashes(v)


class HTTPError429(HTTPError):
    pass


@dataclass
class ModeSourceReport(StaleEntityRemovalSourceReport):
    filtered_spaces: LossyList[str] = dataclasses.field(default_factory=LossyList)

    def report_dropped_space(self, ent_name: str) -> None:
        self.filtered_spaces.append(ent_name)


@platform_name("Mode")
@config_class(ModeConfig)
@support_status(SupportStatus.CERTIFIED)
@capability(SourceCapability.PLATFORM_INSTANCE, "Enabled by default")
@capability(SourceCapability.LINEAGE_COARSE, "Supported by default")
class ModeSource(StatefulIngestionSourceBase):
    """

    This plugin extracts Charts, Reports, and associated metadata from a given Mode workspace. This plugin is in beta and has only been tested
    on PostgreSQL database.

    ### Report

    [/api/{account}/reports/{report}](https://mode.com/developer/api-reference/analytics/reports/) endpoint is used to
    retrieve the following report information.

    - Title and description
    - Last edited by
    - Owner
    - Link to the Report in Mode for exploration
    - Associated charts within the report

    ### Chart

    [/api/{workspace}/reports/{report}/queries/{query}/charts'](https://mode.com/developer/api-reference/analytics/charts/#getChart) endpoint is used to
    retrieve the following information.

    - Title and description
    - Last edited by
    - Owner
    - Link to the chart in Metabase
    - Datasource and lineage information from Report queries.

    The following properties for a chart are ingested in DataHub.

    #### Chart Information
    | Name      | Description                            |
    |-----------|----------------------------------------|
    | `Filters` | Filters applied to the chart           |
    | `Metrics` | Fields or columns used for aggregation |
    | `X`       | Fields used in X-axis                  |
    | `X2`      | Fields used in second X-axis           |
    | `Y`       | Fields used in Y-axis                  |
    | `Y2`      | Fields used in second Y-axis           |


    #### Table Information
    | Name      | Description                  |
    |-----------|------------------------------|
    | `Columns` | Column names in a table      |
    | `Filters` | Filters applied to the table |



    #### Pivot Table Information
    | Name      | Description                            |
    |-----------|----------------------------------------|
    | `Columns` | Column names in a table                |
    | `Filters` | Filters applied to the table           |
    | `Metrics` | Fields or columns used for aggregation |
    | `Rows`    | Row names in a table                   |

    """

    ctx: PipelineContext
    config: ModeConfig
    report: ModeSourceReport
    platform = "mode"

    DIMENSION_TAG_URN = "urn:li:tag:Dimension"
    MEASURE_TAG_URN = "urn:li:tag:Measure"

    tag_definitions: Dict[str, TagPropertiesClass] = {
        DIMENSION_TAG_URN: TagPropertiesClass(
            name=DIMENSION_TAG_URN.split(":")[-1],
            description="A tag that is applied to all dimension fields.",
        ),
        MEASURE_TAG_URN: TagPropertiesClass(
            name=MEASURE_TAG_URN.split(":")[-1],
            description="A tag that is applied to all measures (metrics). Measures are typically the columns that you aggregate on",
        ),
    }

    def __hash__(self):
        return id(self)

    def __init__(self, ctx: PipelineContext, config: ModeConfig):
        super().__init__(config, ctx)
        self.config = config
        self.report = ModeSourceReport()
        self.ctx = ctx

        self.session = requests.session()
        self.session.auth = HTTPBasicAuth(
            self.config.token,
            self.config.password.get_secret_value(),
        )
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "Accept": "application/hal+json",
            }
        )

        # Test the connection
        try:
            self._get_request_json(f"{self.config.connect_uri}/api/verify")
        except HTTPError as http_error:
            self.report.report_failure(
                key="mode-session",
                reason=f"Unable to verify connection"
                f"{self.config.token} information, "
                f"{str(http_error)}",
            )

        self.workspace_uri = f"{self.config.connect_uri}/api/{self.config.workspace}"
        self.space_tokens = self._get_space_name_and_tokens()

    def construct_dashboard(
        self, space_name: str, report_info: dict
    ) -> Optional[DashboardSnapshot]:
        report_token = report_info.get("token", "")

        if not report_token:
            self.report.report_warning(
                key="mode-report",
                reason=f"Report token is missing for {report_info.get('id', '')}",
            )
            return None

        if not report_info.get("id"):
            self.report.report_warning(
                key="mode-report",
                reason=f"Report id is missing for {report_info.get('token', '')}",
            )
            return None

        dashboard_urn = builder.make_dashboard_urn(
            self.platform, report_info.get("id", "")
        )
        dashboard_snapshot = DashboardSnapshot(
            urn=dashboard_urn,
            aspects=[],
        )

        title = report_info.get("name", "")
        description = report_info.get("description", "")

        last_modified = ChangeAuditStamps()
        creator = self._get_creator(
            report_info.get("_links", {}).get("creator", {}).get("href", "")
        )
        if creator:
            modified_actor = builder.make_user_urn(creator)
            if not report_info.get("last_saved_at"):
                # Sometimes mode returns null for last_saved_at.
                # In that case, we use the created_at timestamp instead.
                report_info["last_saved_at"] = report_info.get("created_at")

            modified_ts = int(
                dp.parse(f"{report_info.get('last_saved_at', 'now')}").timestamp()
                * 1000
            )
            created_ts = int(
                dp.parse(f"{report_info.get('created_at', 'now')}").timestamp() * 1000
            )
            last_modified = ChangeAuditStamps(
                created=AuditStamp(time=created_ts, actor=modified_actor),
                lastModified=AuditStamp(time=modified_ts, actor=modified_actor),
            )

        dashboard_info_class = DashboardInfoClass(
            description=description if description else "",
            title=title if title else "",
            charts=self._get_chart_urns(report_token),
            lastModified=last_modified,
            dashboardUrl=f"{self.config.connect_uri}/{self.config.workspace}/reports/{report_token}",
            customProperties={},
        )
        dashboard_snapshot.aspects.append(dashboard_info_class)

        # browse path
        browse_path = BrowsePathsClass(
            paths=[
                f"/mode/{self.config.workspace}/"
                f"{space_name}/"
                f"{title if title else report_info.get('id', '')}"
            ]
        )
        dashboard_snapshot.aspects.append(browse_path)

        # Ownership
        ownership = self._get_ownership(
            self._get_creator(
                report_info.get("_links", {}).get("creator", {}).get("href", "")
            )
        )
        if ownership is not None:
            dashboard_snapshot.aspects.append(ownership)

        return dashboard_snapshot

    @lru_cache(maxsize=None)
    def _get_ownership(self, user: str) -> Optional[OwnershipClass]:
        if user is not None:
            owner_urn = builder.make_user_urn(user)
            ownership: OwnershipClass = OwnershipClass(
                owners=[
                    OwnerClass(
                        owner=owner_urn,
                        type=OwnershipTypeClass.DATAOWNER,
                    )
                ]
            )
            return ownership

        return None

    @lru_cache(maxsize=None)
    def _get_creator(self, href: str) -> Optional[str]:
        user = None
        try:
            user_json = self._get_request_json(f"{self.config.connect_uri}{href}")
            user = (
                user_json.get("username")
                if self.config.owner_username_instead_of_email
                else user_json.get("email")
            )
        except HTTPError as http_error:
            self.report.report_warning(
                key="mode-user",
                reason=f"Unable to retrieve user for {href}, "
                f"Reason: {str(http_error)}",
            )
        return user

    def _get_chart_urns(self, report_token: str) -> list:
        chart_urns = []
        queries = self._get_queries(report_token)
        for query in queries:
            charts = self._get_charts(report_token, query.get("token", ""))
            # build chart urns
            for chart in charts:
                logger.debug(f"Chart: {chart.get('token')}")
                chart_urn = builder.make_chart_urn(
                    self.platform, chart.get("token", "")
                )
                chart_urns.append(chart_urn)

        return chart_urns

    def _get_space_name_and_tokens(self) -> dict:
        space_info = {}
        try:
            logger.debug(f"Retrieving spaces for {self.workspace_uri}")
            payload = self._get_request_json(f"{self.workspace_uri}/spaces?filter=all")
            spaces = payload.get("_embedded", {}).get("spaces", {})
            logger.debug(
                f"Got {len(spaces)} spaces from workspace {self.workspace_uri}"
            )
            for s in spaces:
                logger.debug(f"Space: {s.get('name')}")
                space_name = s.get("name", "")
                if not self.config.space_pattern.allowed(space_name):
                    self.report.report_dropped_space(space_name)
                    logging.debug(f"Skipping space {space_name} due to space pattern")
                    continue
                space_info[s.get("token", "")] = s.get("name", "")
        except HTTPError as http_error:
            self.report.report_failure(
                key="mode-spaces",
                reason=f"Unable to retrieve spaces/collections for {self.workspace_uri}, "
                f"Reason: {str(http_error)}",
            )

        return space_info

    def _get_chart_type(self, token: str, display_type: str) -> Optional[str]:
        type_mapping = {
            "table": ChartTypeClass.TABLE,
            "bar": ChartTypeClass.BAR,
            "line": ChartTypeClass.LINE,
            "stackedBar100": ChartTypeClass.BAR,
            "stackedBar": ChartTypeClass.BAR,
            "hStackedBar": ChartTypeClass.BAR,
            "hStackedBar100": ChartTypeClass.BAR,
            "hBar": ChartTypeClass.BAR,
            "area": ChartTypeClass.AREA,
            "totalArea": ChartTypeClass.AREA,
            "pie": ChartTypeClass.PIE,
            "donut": ChartTypeClass.PIE,
            "scatter": ChartTypeClass.SCATTER,
            "bigValue": ChartTypeClass.TEXT,
            "pivotTable": ChartTypeClass.TABLE,
            "linePlusBar": None,
        }
        if not display_type:
            self.report.report_warning(
                key=f"mode-chart-{token}",
                reason=f"Chart type {display_type} is missing. " f"Setting to None",
            )
            return None
        try:
            chart_type = type_mapping[display_type]
        except KeyError:
            self.report.report_warning(
                key=f"mode-chart-{token}",
                reason=f"Chart type {display_type} not supported. " f"Setting to None",
            )
            chart_type = None

        return chart_type

    def construct_chart_custom_properties(
        self, chart_detail: dict, chart_type: str
    ) -> Dict:
        custom_properties = {}
        metadata = chart_detail.get("encoding", {})
        if chart_type == "table":
            columns = list(chart_detail.get("fieldFormats", {}).keys())
            str_columns = ",".join([c[1:-1] for c in columns])
            filters = metadata.get("filter", [])
            filters = filters[0].get("formula", "") if len(filters) else ""

            custom_properties = {
                "Columns": str_columns,
                "Filters": filters[1:-1] if len(filters) else "",
            }

        elif chart_type == "pivotTable":
            pivot_table = chart_detail.get("pivotTable", {})
            columns = pivot_table.get("columns", [])
            rows = pivot_table.get("rows", [])
            values = pivot_table.get("values", [])
            filters = pivot_table.get("filters", [])

            custom_properties = {
                "Columns": ", ".join(columns) if len(columns) else "",
                "Rows": ", ".join(rows) if len(rows) else "",
                "Metrics": ", ".join(values) if len(values) else "",
                "Filters": ", ".join(filters) if len(filters) else "",
            }
            # list filters in their own row
            for filter in filters:
                custom_properties[f"Filter: {filter}"] = ", ".join(
                    pivot_table.get("filterValues", {}).get(filter, "")
                )
        # Chart
        else:
            x = metadata.get("x", [])
            x2 = metadata.get("x2", [])
            y = metadata.get("y", [])
            y2 = metadata.get("y2", [])
            value = metadata.get("value", [])
            filters = metadata.get("filter", [])

            custom_properties = {
                "X": x[0].get("formula", "") if len(x) else "",
                "Y": y[0].get("formula", "") if len(y) else "",
                "X2": x2[0].get("formula", "") if len(x2) else "",
                "Y2": y2[0].get("formula", "") if len(y2) else "",
                "Metrics": value[0].get("formula", "") if len(value) else "",
                "Filters": filters[0].get("formula", "") if len(filters) else "",
            }

        return custom_properties

    def _get_datahub_friendly_platform(self, adapter, platform):
        # Map adaptor names to what datahub expects in
        # https://github.com/datahub-project/datahub/blob/master/metadata-service/war/src/main/resources/boot/data_platforms.json

        platform_mapping = {
            "jdbc:athena": "athena",
            "jdbc:bigquery": "bigquery",
            "jdbc:druid": "druid",
            "jdbc:hive": "hive",
            "jdbc:mysql": "mysql",
            "jdbc:oracle": "oracle",
            "jdbc:postgresql": "postgres",
            "jdbc:presto": "presto",
            "jdbc:redshift": "redshift",
            "jdbc:snowflake": "snowflake",
            "jdbc:spark": "spark",
            "jdbc:trino": "trino",
            "jdbc:sqlserver": "mssql",
            "jdbc:teradata": "teradata",
        }
        if adapter in platform_mapping:
            return platform_mapping[adapter]
        else:
            self.report.report_warning(
                key=f"mode-platform-{adapter}",
                reason=f"Platform was not found in DataHub. "
                f"Using {platform} name as is",
            )

        return platform

    @lru_cache(maxsize=None)
    def _get_platform_and_dbname(
        self, data_source_id: int
    ) -> Union[Tuple[str, str], Tuple[None, None]]:
        data_sources = []
        try:
            ds_json = self._get_request_json(f"{self.workspace_uri}/data_sources")
            data_sources = ds_json.get("_embedded", {}).get("data_sources", [])
        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-datasource-{data_source_id}",
                reason=f"No data sources found for datasource id: "
                f"{data_source_id}, "
                f"Reason: {str(http_error)}",
            )

        if not data_sources:
            self.report.report_failure(
                key=f"mode-datasource-{data_source_id}",
                reason=f"No data sources found for datasource id: " f"{data_source_id}",
            )
            return None, None

        for data_source in data_sources:
            if data_source.get("id", -1) == data_source_id:
                platform = self._get_datahub_friendly_platform(
                    data_source.get("adapter", ""), data_source.get("name", "")
                )
                database = data_source.get("database", "")
                return platform, database
        else:
            self.report.report_warning(
                key=f"mode-datasource-{data_source_id}",
                reason=f"Cannot create datasource urn for datasource id: "
                f"{data_source_id}",
            )
        return None, None

    def _replace_definitions(self, raw_query: str) -> str:
        query = raw_query
        definitions = re.findall("({{[^}{]+}})", raw_query)
        for definition_variable in definitions:
            definition_name, definition_alias = self._parse_definition_name(
                definition_variable
            )
            definition_query = self._get_definition(definition_name)
            # if unable to retrieve definition, then replace the {{}} so that it doesn't get picked up again in recursive call
            if definition_query is not None:
                query = query.replace(
                    definition_variable, f"({definition_query}) as {definition_alias}"
                )
            else:
                query = query.replace(
                    definition_variable, f"{definition_name} as {definition_alias}"
                )
            query = self._replace_definitions(query)

        return query

    def _parse_definition_name(self, definition_variable: str) -> Tuple[str, str]:
        name, alias = "", ""
        # i.e '{{ @join_on_definition as alias}}'
        name_match = re.findall("@[a-zA-z]+", definition_variable)
        if len(name_match):
            name = name_match[0][1:]
        alias_match = re.findall(
            r"as\s+\S+", definition_variable
        )  # i.e ['as    alias_name']
        if len(alias_match):
            alias_match = alias_match[0].split(" ")
            alias = alias_match[-1]

        return name, alias

    @lru_cache(maxsize=None)
    def _get_definition(self, definition_name):
        try:
            definition_json = self._get_request_json(
                f"{self.workspace_uri}/definitions"
            )
            definitions = definition_json.get("_embedded", {}).get("definitions", [])
            for definition in definitions:
                if definition.get("name", "") == definition_name:
                    return definition.get("source", "")

        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-definition-{definition_name}",
                reason=f"Unable to retrieve definition for {definition_name}, "
                f"Reason: {str(http_error)}",
            )
        return None

    @lru_cache(maxsize=None)
    def _get_source_from_query(self, raw_query: str) -> set:
        query = self._replace_definitions(raw_query)
        parser = LineageRunner(query)
        source_paths = set()
        try:
            for table in parser.source_tables:
                sources = str(table).split(".")
                source_schema, source_table = sources[-2], sources[-1]
                if source_schema == "<default>":
                    source_schema = str(self.config.default_schema)

                source_paths.add(f"{source_schema}.{source_table}")
        except Exception as e:
            self.report.report_failure(
                key="mode-query",
                reason=f"Unable to retrieve lineage from query. "
                f"Query: {raw_query} "
                f"Reason: {str(e)} ",
            )

        return source_paths

    def _get_datasource_urn(
        self,
        platform: str,
        platform_instance: Optional[str],
        database: str,
        source_tables: List[str],
    ) -> List[str]:
        dataset_urn = None
        if platform or database is not None:
            dataset_urn = [
                builder.make_dataset_urn_with_platform_instance(
                    platform,
                    f"{database}.{s_table}",
                    platform_instance=platform_instance,
                    env=self.config.env,
                )
                for s_table in source_tables
            ]

        return dataset_urn

    def get_custom_props_from_dict(self, obj: dict, keys: List[str]) -> Optional[dict]:
        return {key: str(obj[key]) for key in keys if obj.get(key)} or None

    def get_dataset_urn_from_query(self, query_data: dict) -> str:
        return builder.make_dataset_urn_with_platform_instance(
            platform=self.platform,
            name=str(query_data.get("id")),
            platform_instance=None,
            env=self.config.env,
        )

    def get_query_instance_urn_from_query(self, query_data: dict) -> str:
        id = query_data.get("id")
        last_run_id = query_data.get("last_run_id")
        data_source_id = query_data.get("data_source_id")
        return QueryUrn(f"{id}.{data_source_id}.{last_run_id}").urn()

    def _get_upstream_warehouse_urn_for_query(self, query: dict) -> List[str]:
        # create datasource urn
        platform, db_name = self._get_platform_and_dbname(query.get("data_source_id"))
        source_tables = self._get_source_from_query(query.get("raw_query"))
        if not platform or not db_name or not source_tables:
            return []
        datasource_urn = self._get_datasource_urn(
            platform=platform,
            platform_instance=(
                self.config.platform_instance_map.get(platform)
                if platform and self.config.platform_instance_map
                else None
            ),
            database=db_name,
            source_tables=list(source_tables),
        )
        return datasource_urn

    def set_field_tags(self, fields: List[SchemaFieldClass]) -> None:
        for field in fields:
            # It is not clear how to distinguish between measures and dimensions in Mode.
            # We are using a heuristic to tag fields that are not named `id` or `_id` or ends with '_number' and are not of type `NumberType` as dimensions.
            # This is a heuristic and may not be accurate.
            if field.type.type.RECORD_SCHEMA.name in ["NumberType"] and (
                not field.fieldPath.endswith("_number")
                and not re.findall(r"(^(id)[_\d]?)|([_\d?](id)$)", field.fieldPath)
            ):
                tag = TagAssociationClass(tag=self.MEASURE_TAG_URN)
            else:
                tag = TagAssociationClass(tag=self.DIMENSION_TAG_URN)
            field.globalTags = GlobalTagsClass(tags=[tag])

    def construct_query_from_api_data(
        self,
        report_token: str,
        query_data: dict,
    ) -> Iterable[MetadataWorkUnit]:
        query_urn = self.get_dataset_urn_from_query(query_data)

        dataset_props = DatasetPropertiesClass(
            name=query_data.get("name"),
            description="",
            externalUrl=f"{self.config.connect_uri}/{self.config.workspace}/reports/{report_token}/details/queries/{query_data.get('token')}",
            customProperties=self.get_custom_props_from_dict(
                query_data,
                [
                    "id" "created_at",
                    "updated_at",
                    "last_run_id",
                    "data_source_id",
                    "explorations_count",
                    "report_imports_count",
                    "dbt_metric_id",
                ],
            ),
        )

        yield (
            MetadataChangeProposalWrapper(
                entityType="dataset",
                changeType=ChangeTypeClass.UPSERT,
                entityUrn=query_urn,
                aspectName="datasetProperties",
                aspect=dataset_props,
            ).as_workunit()
        )

        subtypes = SubTypesClass(typeNames=(["Query"]))
        yield (
            MetadataChangeProposalWrapper(
                entityType="dataset",
                changeType=ChangeTypeClass.UPSERT,
                entityUrn=query_urn,
                aspectName="subTypes",
                aspect=subtypes,
            ).as_workunit()
        )

        (
            upstream_warehouse_platform,
            upstream_warehouse_db_name,
        ) = self._get_platform_and_dbname(query_data.get("data_source_id"))

        if upstream_warehouse_platform is None:
            # this means we can't infer the platform
            return

        parsed_query_object = create_lineage_sql_parsed_result(
            query=query_data["raw_query"],
            default_db=upstream_warehouse_db_name,
            platform=upstream_warehouse_platform,
            platform_instance=(
                self.config.platform_instance_map.get(upstream_warehouse_platform)
                if upstream_warehouse_platform and self.config.platform_instance_map
                else None
            ),
            env=self.config.env,
            graph=self.ctx.graph,
        )

        schema_fields = infer_output_schema(parsed_query_object)
        if schema_fields:

            schema_metadata = SchemaMetadataClass(
                schemaName="mode_query",
                platform=f"urn:li:dataPlatform:{self.platform}",
                version=0,
                fields=schema_fields,
                hash="",
                platformSchema=OtherSchemaClass(rawSchema=""),
            )
            if self.config.tag_measures_and_dimensions:
                self.set_field_tags(schema_fields)

            yield (
                MetadataChangeProposalWrapper(
                    entityType="dataset",
                    changeType=ChangeTypeClass.UPSERT,
                    entityUrn=query_urn,
                    aspectName="schemaMetadata",
                    aspect=schema_metadata,
                ).as_workunit()
            )

        yield from self.get_upstream_lineage_for_parsed_sql(
            query_data, parsed_query_object
        )

        operation = OperationClass(
            operationType=OperationTypeClass.UPDATE,
            lastUpdatedTimestamp=int(
                dp.parse(query_data.get("updated_at", "now")).timestamp() * 1000
            ),
            timestampMillis=int(datetime.now(tz=timezone.utc).timestamp() * 1000),
        )

        yield MetadataChangeProposalWrapper(
            entityUrn=query_urn,
            aspect=operation,
        ).as_workunit()

        creator = self._get_creator(
            query_data.get("_links", {}).get("creator", {}).get("href", "")
        )
        modified_actor = builder.make_user_urn(
            creator if creator is not None else "unknown"
        )

        created_ts = int(
            dp.parse(query_data.get("created_at", "now")).timestamp() * 1000
        )
        modified_ts = int(
            dp.parse(query_data.get("updated_at", "now")).timestamp() * 1000
        )

        query_instance_urn = self.get_query_instance_urn_from_query(query_data)
        value = query_data.get("raw_query")
        if value:
            query_properties = QueryPropertiesClass(
                statement=QueryStatementClass(
                    value=value,
                    language=QueryLanguageClass.SQL,
                ),
                source=QuerySourceClass.SYSTEM,
                created=AuditStamp(time=created_ts, actor=modified_actor),
                lastModified=AuditStamp(time=modified_ts, actor=modified_actor),
            )

            yield MetadataChangeProposalWrapper(
                entityType="query",
                changeType=ChangeTypeClass.UPSERT,
                entityUrn=query_instance_urn,
                aspectName="queryProperties",
                aspect=query_properties,
            ).as_workunit()

    def get_upstream_lineage_for_parsed_sql(
        self, query_data: dict, parsed_query_object: SqlParsingResult
    ) -> List[MetadataWorkUnit]:
        wu = []
        query_urn = self.get_dataset_urn_from_query(query_data)

        if parsed_query_object is None:
            logger.info(
                f"Failed to extract column level lineage from datasource {query_urn}"
            )
            return []
        if parsed_query_object.debug_info.error:
            logger.info(
                f"Failed to extract column level lineage from datasource {query_urn}: {parsed_query_object.debug_info.error}"
            )
            return []

        cll: List[ColumnLineageInfo] = (
            parsed_query_object.column_lineage
            if parsed_query_object.column_lineage is not None
            else []
        )

        fine_grained_lineages: List[FineGrainedLineageClass] = []

        table_urn = None

        for cll_info in cll:
            if table_urn is None:
                for column_ref in cll_info.upstreams:
                    table_urn = column_ref.table
                    break

            downstream = (
                [builder.make_schema_field_urn(query_urn, cll_info.downstream.column)]
                if cll_info.downstream is not None
                and cll_info.downstream.column is not None
                else []
            )
            upstreams = [
                builder.make_schema_field_urn(column_ref.table, column_ref.column)
                for column_ref in cll_info.upstreams
            ]
            fine_grained_lineages.append(
                FineGrainedLineageClass(
                    downstreamType=FineGrainedLineageDownstreamTypeClass.FIELD,
                    downstreams=downstream,
                    upstreamType=FineGrainedLineageUpstreamTypeClass.FIELD_SET,
                    upstreams=upstreams,
                )
            )

        upstream_lineage = UpstreamLineageClass(
            upstreams=[
                UpstreamClass(
                    type=DatasetLineageTypeClass.TRANSFORMED,
                    dataset=input_table_urn,
                    query=self.get_query_instance_urn_from_query(query_data),
                )
                for input_table_urn in parsed_query_object.in_tables
            ],
            fineGrainedLineages=fine_grained_lineages,
        )

        wu.append(
            MetadataChangeProposalWrapper(
                entityType="dataset",
                changeType=ChangeTypeClass.UPSERT,
                entityUrn=query_urn,
                aspectName="upstreamLineage",
                aspect=upstream_lineage,
            ).as_workunit()
        )

        return wu

    def get_formula_columns(self, node: Dict, columns: Set[str] = set()) -> Set[str]:
        if isinstance(node, dict):
            for key, item in node.items():
                node = item
                if isinstance(item, dict):
                    self.get_formula_columns(node, columns)
                elif isinstance(node, list):
                    for i in node:
                        if isinstance(i, dict):
                            self.get_formula_columns(i, columns)
                elif isinstance(node, str):
                    if key == "formula":
                        column_names = re.findall(r"\[(.+?)\]", node)
                        columns.update(column_names)
        return columns

    def get_input_fields(
        self, chart_urn: str, chart_data: Dict, chart_fields: List[str], query_urn: str
    ) -> Iterable[MetadataWorkUnit]:
        fields = self.get_formula_columns(chart_data)

        input_fields = []

        for field in sorted(fields):
            if field.lower() not in chart_fields:
                continue
            input_field = InputFieldClass(
                schemaFieldUrn=builder.make_schema_field_urn(query_urn, field.lower()),
                schemaField=SchemaFieldClass(
                    fieldPath=field.lower(),
                    type=SchemaFieldDataTypeClass(type=StringTypeClass()),
                    nativeDataType="string",
                ),
            )
            input_fields.append(input_field)

        if not input_fields:
            return

        inputFields = InputFieldsClass(fields=input_fields)

        yield MetadataChangeProposalWrapper(
            entityUrn=chart_urn,
            aspect=inputFields,
        ).as_workunit()

    def construct_chart_from_api_data(
        self, chart_data: dict, chart_fields: List[str], query: dict, path: str
    ) -> Iterable[MetadataWorkUnit]:
        chart_urn = builder.make_chart_urn(self.platform, chart_data.get("token", ""))
        chart_snapshot = ChartSnapshot(
            urn=chart_urn,
            aspects=[],
        )

        last_modified = ChangeAuditStamps()
        creator = self._get_creator(
            chart_data.get("_links", {}).get("creator", {}).get("href", "")
        )
        if creator is not None:
            modified_actor = builder.make_user_urn(creator)
            created_ts = int(
                dp.parse(chart_data.get("created_at", "now")).timestamp() * 1000
            )
            modified_ts = int(
                dp.parse(chart_data.get("updated_at", "now")).timestamp() * 1000
            )
            last_modified = ChangeAuditStamps(
                created=AuditStamp(time=created_ts, actor=modified_actor),
                lastModified=AuditStamp(time=modified_ts, actor=modified_actor),
            )

        chart_detail = (
            chart_data.get("view", {})
            if len(chart_data.get("view", {})) != 0
            else chart_data.get("view_vegas", {})
        )

        mode_chart_type = chart_detail.get("chartType", "") or chart_detail.get(
            "selectedChart", ""
        )
        chart_type = self._get_chart_type(chart_data.get("token", ""), mode_chart_type)
        description = (
            chart_detail.get("description")
            or chart_detail.get("chartDescription")
            or ""
        )
        title = chart_detail.get("title") or chart_detail.get("chartTitle") or ""

        # create datasource urn
        custom_properties = self.construct_chart_custom_properties(
            chart_detail, mode_chart_type
        )

        query_urn = self.get_dataset_urn_from_query(query)
        custom_properties["upstream_fields"] = "profile_id"
        # Chart Info
        chart_info = ChartInfoClass(
            type=chart_type,
            description=description,
            title=title,
            lastModified=last_modified,
            chartUrl=f"{self.config.connect_uri}"
            f"{chart_data.get('_links', {}).get('report_viz_web', {}).get('href', '')}",
            inputs=[query_urn],
            customProperties=custom_properties,
            inputEdges=[],
        )
        chart_snapshot.aspects.append(chart_info)

        query_urn = self.get_dataset_urn_from_query(query)
        yield from self.get_input_fields(chart_urn, chart_data, chart_fields, query_urn)
        # Browse Path
        browse_path = BrowsePathsClass(paths=[path])
        chart_snapshot.aspects.append(browse_path)

        # Query
        chart_query = ChartQueryClass(
            rawQuery=query.get("raw_query", ""),
            type=ChartQueryTypeClass.SQL,
        )
        chart_snapshot.aspects.append(chart_query)

        # Ownership
        ownership = self._get_ownership(
            self._get_creator(
                chart_data.get("_links", {}).get("creator", {}).get("href", "")
            )
        )
        if ownership is not None:
            chart_snapshot.aspects.append(ownership)

        mce = MetadataChangeEvent(proposedSnapshot=chart_snapshot)
        yield MetadataWorkUnit(id=chart_snapshot.urn, mce=mce)

    @lru_cache(maxsize=None)
    def _get_reports(self, space_token: str) -> list:
        reports = []
        try:
            reports_json = self._get_request_json(
                f"{self.workspace_uri}/spaces/{space_token}/reports"
            )
            reports = reports_json.get("_embedded", {}).get("reports", {})
        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-report-{space_token}",
                reason=f"Unable to retrieve reports for space token: {space_token}, "
                f"Reason: {str(http_error)}",
            )
        return reports

    @lru_cache(maxsize=None)
    def _get_queries(self, report_token: str) -> list:
        queries = []
        try:
            queries_json = self._get_request_json(
                f"{self.workspace_uri}/reports/{report_token}/queries"
            )
            queries = queries_json.get("_embedded", {}).get("queries", {})
        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-query-{report_token}",
                reason=f"Unable to retrieve queries for report token: {report_token}, "
                f"Reason: {str(http_error)}",
            )
        return queries

    @lru_cache(maxsize=None)
    def _get_last_query_run(
        self, report_token: str, report_run_id: str, query_run_id: str
    ) -> Dict:
        try:
            queries_json = self._get_request_json(
                f"{self.workspace_uri}/reports/{report_token}/runs/{report_run_id}/query_runs{query_run_id}"
            )
            queries = queries_json.get("_embedded", {}).get("queries", {})
        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-query-{report_token}",
                reason=f"Unable to retrieve queries for report token: {report_token}, "
                f"Reason: {str(http_error)}",
            )
            return {}
        return queries

    @lru_cache(maxsize=None)
    def _get_charts(self, report_token: str, query_token: str) -> list:
        charts = []
        try:
            charts_json = self._get_request_json(
                f"{self.workspace_uri}/reports/{report_token}"
                f"/queries/{query_token}/charts"
            )
            charts = charts_json.get("_embedded", {}).get("charts", {})
        except HTTPError as http_error:
            self.report.report_failure(
                key=f"mode-chart-{report_token}-{query_token}",
                reason=f"Unable to retrieve charts: "
                f"Report token: {report_token} "
                f"Query token: {query_token}, "
                f"Reason: {str(http_error)}",
            )
        return charts

    def _get_request_json(self, url: str) -> Dict:
        r = tenacity.Retrying(
            wait=wait_exponential(
                multiplier=self.config.api_options.retry_backoff_multiplier,
                max=self.config.api_options.max_retry_interval,
            ),
            retry=retry_if_exception_type(HTTPError429),
            stop=stop_after_attempt(self.config.api_options.max_attempts),
        )

        @r.wraps
        def get_request():
            try:
                response = self.session.get(url)
                response.raise_for_status()
                return response.json()
            except HTTPError as http_error:
                error_response = http_error.response
                if error_response.status_code == 429:
                    # respect Retry-After
                    sleep_time = error_response.headers.get("retry-after")
                    if sleep_time is not None:
                        time.sleep(float(sleep_time))
                    raise HTTPError429

                raise http_error

        return get_request()

    @staticmethod
    def create_embed_aspect_mcp(
        entity_urn: str, embed_url: str
    ) -> MetadataChangeProposalWrapper:
        return MetadataChangeProposalWrapper(
            entityUrn=entity_urn,
            aspect=EmbedClass(renderUrl=embed_url),
        )

    def emit_dashboard_mces(self) -> Iterable[MetadataWorkUnit]:
        for space_token, space_name in self.space_tokens.items():
            reports = self._get_reports(space_token)
            for report in reports:
                logger.debug(
                    f"Report: name: {report.get('name')} token: {report.get('token')}"
                )
                dashboard_snapshot_from_report = self.construct_dashboard(
                    space_name, report
                )

                if dashboard_snapshot_from_report is None:
                    continue

                mce = MetadataChangeEvent(
                    proposedSnapshot=dashboard_snapshot_from_report
                )

                mcpw = MetadataChangeProposalWrapper(
                    entityType="dashboard",
                    changeType=ChangeTypeClass.UPSERT,
                    entityUrn=dashboard_snapshot_from_report.urn,
                    aspectName="subTypes",
                    aspect=SubTypesClass(typeNames=["Report"]),
                )
                yield mcpw.as_workunit()

                usage_statistics = DashboardUsageStatisticsClass(
                    timestampMillis=round(datetime.now().timestamp() * 1000),
                    viewsCount=report.get("view_count", 0),
                )

                yield MetadataChangeProposalWrapper(
                    entityType="dashboard",
                    changeType=ChangeTypeClass.UPSERT,
                    entityUrn=dashboard_snapshot_from_report.urn,
                    aspectName="dashboardUsageStatistics",
                    aspect=usage_statistics,
                ).as_workunit()

                if self.config.ingest_embed_url is True:
                    yield self.create_embed_aspect_mcp(
                        entity_urn=dashboard_snapshot_from_report.urn,
                        embed_url=f"{self.config.connect_uri}/{self.config.workspace}/reports/{report.get('token')}/embed",
                    ).as_workunit()

                yield MetadataWorkUnit(id=dashboard_snapshot_from_report.urn, mce=mce)

    def emit_chart_mces(self) -> Iterable[MetadataWorkUnit]:
        # Space/collection -> report -> query -> Chart
        for space_token, space_name in self.space_tokens.items():
            reports = self._get_reports(space_token)
            for report in reports:
                report_token = report.get("token", "")
                queries = self._get_queries(report_token)
                for query in queries:
                    query_mcps = self.construct_query_from_api_data(report_token, query)
                    chart_fields: List[str] = []
                    for wu in query_mcps:
                        if (
                            isinstance(wu.metadata, MetadataChangeProposalWrapper)
                            and wu.metadata.aspectName == "schemaMetadata"
                        ):
                            if isinstance(wu.metadata.aspect, SchemaMetadataClass):
                                schema_metadata = wu.metadata.aspect
                                for field in schema_metadata.fields:
                                    chart_fields.append(field.fieldPath)

                        yield wu

                    charts = self._get_charts(report_token, query.get("token", ""))
                    # build charts
                    for chart in charts:
                        view = chart.get("view") or chart.get("view_vegas")
                        chart_name = view.get("title") or view.get("chartTitle") or ""
                        path = f"/mode/{self.config.workspace}/{space_name}/{report.get('name')}/{query.get('name')}/{chart_name}"
                        yield from self.construct_chart_from_api_data(
                            chart, chart_fields, query, path
                        )

    @classmethod
    def create(cls, config_dict: dict, ctx: PipelineContext) -> "ModeSource":
        config: ModeConfig = ModeConfig.parse_obj(config_dict)
        return cls(ctx, config)

    def get_workunit_processors(self) -> List[Optional[MetadataWorkUnitProcessor]]:
        return [
            *super().get_workunit_processors(),
            StaleEntityRemovalHandler.create(
                self, self.config, self.ctx
            ).workunit_processor,
        ]

    def get_workunits_internal(self) -> Iterable[MetadataWorkUnit]:
        yield from self.emit_dashboard_mces()
        yield from self.emit_chart_mces()

    def get_report(self) -> SourceReport:
        return self.report
