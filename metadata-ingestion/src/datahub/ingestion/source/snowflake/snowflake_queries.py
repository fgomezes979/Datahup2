import functools
import json
import logging
import pathlib
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Optional, Union

import pydantic
from typing_extensions import Self

from datahub.configuration.source_common import (
    EnvConfigMixin,
    LowerCaseDatasetUrnConfigMixin,
    PlatformInstanceConfigMixin,
)
from datahub.configuration.time_window_config import (
    BaseTimeWindowConfig,
    BucketDuration,
)
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.source import Source, SourceReport
from datahub.ingestion.api.source_helpers import auto_workunit
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.source.snowflake.snowflake_connection import (
    SnowflakeConnectionConfig,
)
from datahub.ingestion.source.snowflake.snowflake_query import SnowflakeQuery
from datahub.ingestion.source.snowflake.snowflake_utils import SnowflakeCommonMixin
from datahub.ingestion.source.usage.usage_common import BaseUsageConfig
from datahub.metadata._urns.urn_defs import CorpUserUrn
from datahub.sql_parsing.sql_parsing_aggregator import (
    KnownLineageMapping,
    PreparsedQuery,
    SqlAggregatorReport,
    SqlParsingAggregator,
)
from datahub.sql_parsing.sql_parsing_common import QueryType
from datahub.sql_parsing.sqlglot_lineage import (
    ColumnLineageInfo,
    ColumnRef,
    DownstreamColumnRef,
)
from datahub.utilities.file_backed_collections import ConnectionWrapper, FileBackedList

logger = logging.getLogger(__name__)


class SnowflakeQueriesConfig(
    PlatformInstanceConfigMixin, EnvConfigMixin, LowerCaseDatasetUrnConfigMixin
):
    connection: SnowflakeConnectionConfig

    # TODO: Support stateful ingestion for the time windows.
    window: BaseTimeWindowConfig = BaseTimeWindowConfig()

    # TODO: make this a proper allow/deny pattern
    deny_usernames: List[str] = []

    # TODO: support temporary_tables_pattern

    local_temp_path: Optional[pathlib.Path] = None
    # TODO: support copying files to s3

    convert_urns_to_lowercase: bool = pydantic.Field(
        # Override the default.
        default=True,
        description="Whether to convert dataset urns to lowercase.",
    )

    include_lineage: bool = True
    include_queries: bool = True
    include_usage_statistics: bool = True
    include_query_usage_statistics: bool = False
    include_operations: bool = True


@dataclass
class SnowflakeQueriesReport(SourceReport):
    window: Optional[BaseTimeWindowConfig] = None

    sql_aggregator: Optional[SqlAggregatorReport] = None


class SnowflakeQueriesSource(Source, SnowflakeCommonMixin):
    def __init__(self, ctx: PipelineContext, config: SnowflakeQueriesConfig):
        self.ctx = ctx
        self.config = config
        self.report = SnowflakeQueriesReport()

        self.platform = "snowflake"

        self.aggregator = SqlParsingAggregator(
            platform=self.platform,
            platform_instance=self.config.platform_instance,
            env=self.config.env,
            # graph=self.ctx.graph,
            generate_lineage=self.config.include_lineage,
            generate_queries=self.config.include_queries,
            generate_usage_statistics=self.config.include_usage_statistics,
            generate_query_usage_statistics=self.config.include_query_usage_statistics,
            usage_config=BaseUsageConfig(
                bucket_duration=self.config.window.bucket_duration,
                start_time=self.config.window.start_time,
                end_time=self.config.window.end_time,
                # TODO make the rest of the fields configurable
            ),
            generate_operations=self.config.include_operations,
            format_queries=False,
        )
        self.report.sql_aggregator = self.aggregator.report

    @classmethod
    def create(cls, config_dict: dict, ctx: PipelineContext) -> Self:
        config = SnowflakeQueriesConfig.parse_obj(config_dict)
        return cls(ctx, config)

    @functools.cached_property
    def local_temp_path(self) -> pathlib.Path:
        if self.config.local_temp_path:
            assert self.config.local_temp_path.is_dir()
            return self.config.local_temp_path

        path = pathlib.Path(tempfile.mkdtemp())
        path.mkdir(parents=True, exist_ok=True)
        logger.info(f"Using local temp path: {path}")
        return path

    def get_workunits_internal(
        self,
    ) -> Iterable[MetadataWorkUnit]:
        self.report.window = self.config.window

        # TODO: Add some logic to check if the cached audit log is stale or not.
        audit_log_file = self.local_temp_path / "audit_log.sqlite"
        use_cached_audit_log = audit_log_file.exists()

        if use_cached_audit_log:
            logger.info("Using cached audit log")
            shared_connection = ConnectionWrapper(audit_log_file)
            queries = FileBackedList(shared_connection)
        else:
            audit_log_file.unlink(missing_ok=True)

            shared_connection = ConnectionWrapper(audit_log_file)
            queries = FileBackedList(shared_connection)

            logger.info("Fetching audit log")
            for entry in self.fetch_audit_log():
                queries.append(entry)

        for query in queries:
            self.aggregator.add(query)

        yield from auto_workunit(self.aggregator.gen_metadata())

    def fetch_audit_log(
        self,
    ) -> Iterable[Union[KnownLineageMapping, PreparsedQuery]]:
        """
        # TODO: we need to fetch this info from somewhere
        discovered_tables = []

        snowflake_lineage_v2 = SnowflakeLineageExtractor(
            config=self.config,  # type: ignore
            report=self.report,  # type: ignore
            dataset_urn_builder=self.gen_dataset_urn,
            redundant_run_skip_handler=None,
            sql_aggregator=self.aggregator,  # TODO this should be unused
        )

        for (
            known_lineage_mapping
        ) in snowflake_lineage_v2._populate_external_lineage_from_copy_history(
            discovered_tables=discovered_tables
        ):
            interim_results.append(known_lineage_mapping)

        for (
            known_lineage_mapping
        ) in snowflake_lineage_v2._populate_external_lineage_from_show_query(
            discovered_tables=discovered_tables
        ):
            interim_results.append(known_lineage_mapping)
        """

        audit_log_query = _build_enriched_audit_log_query(
            start_time=self.config.window.start_time,
            end_time=self.config.window.end_time,
            bucket_duration=self.config.window.bucket_duration,
            deny_usernames=self.config.deny_usernames,
        )

        conn = self.config.connection.get_connection()
        resp = conn.query(audit_log_query)

        for row in resp:
            assert isinstance(row, dict)
            try:
                entry = self._parse_audit_log_response(row)
            except Exception as e:
                self.report.warning(
                    "Error parsing audit log row",
                    context=f"{e}",
                    exc=e,
                )
            else:
                yield entry

    # HACK: This makes mypy happy with our usage of the mixin methods.
    gen_dataset_urn = SnowflakeCommonMixin.gen_dataset_urn
    snowflake_identifier = SnowflakeCommonMixin.snowflake_identifier

    def _parse_audit_log_response(self, row: Dict[str, Any]) -> PreparsedQuery:
        json_fields = {
            "DIRECT_OBJECTS_ACCESSED",
            "OBJECTS_MODIFIED",
        }

        res = {}
        for key, value in row.items():
            if key in json_fields and value:
                value = json.loads(value)
            key = key.lower()
            res[key] = value

        direct_objects_accessed = res["direct_objects_accessed"]
        objects_modified = res["objects_modified"]

        upstreams = []
        column_usage = {}

        for obj in direct_objects_accessed:
            dataset = self.gen_dataset_urn(self.snowflake_identifier(obj["objectName"]))

            columns = set()
            for modified_column in obj["columns"]:
                columns.add(self.snowflake_identifier(modified_column["columnName"]))

            upstreams.append(dataset)
            column_usage[dataset] = columns

        downstream = None
        column_lineage = None
        for obj in objects_modified:
            # We don't expect there to be more than one object modified.
            # TODO: Warn if that happens.

            downstream = self.gen_dataset_urn(
                self.snowflake_identifier(obj["objectName"])
            )
            column_lineage = []
            for modified_column in obj["columns"]:
                column_lineage.append(
                    ColumnLineageInfo(
                        downstream=DownstreamColumnRef(
                            dataset=downstream,
                            column=self.snowflake_identifier(
                                modified_column["columnName"]
                            ),
                        ),
                        upstreams=[
                            ColumnRef(
                                table=self.gen_dataset_urn(
                                    self.snowflake_identifier(upstream["objectName"])
                                ),
                                column=self.snowflake_identifier(
                                    upstream["columnName"]
                                ),
                            )
                            for upstream in modified_column["directSources"]
                            if upstream["objectDomain"]
                            in SnowflakeQuery.ACCESS_HISTORY_TABLE_VIEW_DOMAINS
                        ],
                    )
                )

        # TODO: Support filtering the table names.
        # if objects_modified:
        #     breakpoint()

        # TODO implement email address mapping
        user = CorpUserUrn(res["user_name"])

        timestamp: datetime = res["query_start_time"]
        timestamp = timestamp.astimezone(timezone.utc)

        # TODO need to map snowflake query types to ours
        query_type = SNOWFLAKE_QUERY_TYPE_MAPPING.get(
            res["query_type"], QueryType.UNKNOWN
        )

        entry = PreparsedQuery(
            query_id=res["query_fingerprint"],
            query_text=res["query_text"],
            upstreams=upstreams,
            downstream=downstream,
            column_lineage=column_lineage,
            column_usage=column_usage,
            inferred_schema=None,
            confidence_score=1,
            query_count=res["query_count"],
            user=user,
            timestamp=timestamp,
            session_id=res["session_id"],
            query_type=query_type,
        )
        return entry

    def get_report(self) -> SnowflakeQueriesReport:
        return self.report


# Make sure we don't try to generate too much info for a single query.
_MAX_TABLES_PER_QUERY = 20


def _build_enriched_audit_log_query(
    start_time: datetime,
    end_time: datetime,
    bucket_duration: BucketDuration,
    deny_usernames: Optional[List[str]],
) -> str:
    start_time_millis = int(start_time.timestamp() * 1000)
    end_time_millis = int(end_time.timestamp() * 1000)

    users_filter = ""
    if deny_usernames:
        user_not_in = ",".join(f"'{user.upper()}'" for user in deny_usernames)
        users_filter = f"user_name NOT IN ({user_not_in})"

    time_bucket_size = bucket_duration.value
    assert time_bucket_size in ("HOUR", "DAY", "MONTH")

    return f"""\
WITH
fingerprinted_queries as (
    SELECT *,
        -- TODO: Generate better fingerprints for each query by pushing down regex logic.
        query_history.query_parameterized_hash as query_fingerprint
    FROM
        snowflake.account_usage.query_history
    WHERE
        query_history.start_time >= to_timestamp_ltz({start_time_millis}, 3)
        AND query_history.start_time < to_timestamp_ltz({end_time_millis}, 3)
        AND execution_status = 'SUCCESS'
        AND {users_filter or 'TRUE'}
)
, deduplicated_queries as (
    SELECT
        *,
        DATE_TRUNC(
            {time_bucket_size},
            CONVERT_TIMEZONE('UTC', start_time)
        ) AS bucket_start_time,
        COUNT(*) OVER (PARTITION BY bucket_start_time, query_fingerprint) AS query_count,
    FROM
        fingerprinted_queries
    QUALIFY
        ROW_NUMBER() OVER (PARTITION BY bucket_start_time, query_fingerprint ORDER BY start_time DESC) = 1
)
, raw_access_history AS (
    SELECT
        query_id,
        query_start_time,
        user_name,
        direct_objects_accessed,
        objects_modified,
    FROM
        snowflake.account_usage.access_history
    WHERE
        query_start_time >= to_timestamp_ltz({start_time_millis}, 3)
        AND query_start_time < to_timestamp_ltz({end_time_millis}, 3)
        AND {users_filter or 'TRUE'}
        AND query_id IN (
            SELECT query_id FROM deduplicated_queries
        )
)
, filtered_access_history AS (
    -- TODO: Add table filter clause.
    SELECT
        query_id,
        query_start_time,
        ARRAY_SLICE(
            FILTER(direct_objects_accessed, o -> o:objectDomain IN {SnowflakeQuery.ACCESS_HISTORY_TABLE_VIEW_DOMAINS_FILTER}),
            0, {_MAX_TABLES_PER_QUERY}
        ) as direct_objects_accessed,
        -- TODO: Drop the columns.baseSources subfield.
        FILTER(objects_modified, o -> o:objectDomain IN {SnowflakeQuery.ACCESS_HISTORY_TABLE_VIEW_DOMAINS_FILTER}) as objects_modified,
    FROM raw_access_history
    WHERE ( array_size(direct_objects_accessed) > 0 or array_size(objects_modified) > 0 )
)
, query_access_history AS (
    SELECT
        q.bucket_start_time,
        q.query_id,
        q.query_fingerprint,
        q.query_count,
        q.session_id AS "SESSION_ID",
        q.start_time AS "QUERY_START_TIME",
        q.total_elapsed_time AS "QUERY_DURATION",
        q.query_text AS "QUERY_TEXT",
        q.query_type AS "QUERY_TYPE",
        q.database_name as "DEFAULT_DB",
        q.schema_name as "DEFAULT_SCHEMA",
        q.rows_inserted AS "ROWS_INSERTED",
        q.rows_updated AS "ROWS_UPDATED",
        q.rows_deleted AS "ROWS_DELETED",
        q.user_name AS "USER_NAME",
        q.role_name AS "ROLE_NAME",
        a.direct_objects_accessed,
        a.objects_modified,
    FROM deduplicated_queries q
    JOIN filtered_access_history a USING (query_id)
)
SELECT * FROM query_access_history
"""


SNOWFLAKE_QUERY_TYPE_MAPPING = {
    "INSERT": QueryType.INSERT,
    "UPDATE": QueryType.UPDATE,
    "DELETE": QueryType.DELETE,
    "CREATE": QueryType.CREATE_OTHER,
    "CREATE_TABLE": QueryType.CREATE_DDL,
    "CREATE_VIEW": QueryType.CREATE_VIEW,
    "CREATE_TABLE_AS_SELECT": QueryType.CREATE_TABLE_AS_SELECT,
    "MERGE": QueryType.MERGE,
    "COPY": QueryType.UNKNOWN,
    "TRUNCATE_TABLE": QueryType.UNKNOWN,
}
