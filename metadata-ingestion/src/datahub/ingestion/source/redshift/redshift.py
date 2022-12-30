import logging
from collections import defaultdict
from typing import Dict, Iterable, List, Optional, Type, Union, cast

import humanfriendly

# These imports verify that the dependencies are available.
import psycopg2  # noqa: F401
import pydantic
import redshift_connector

from datahub.emitter.mce_builder import (
    make_data_platform_urn,
    make_dataset_urn_with_platform_instance,
    make_tag_urn,
)
from datahub.emitter.mcp_builder import wrap_aspect_as_workunit
from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.api.decorators import (
    SourceCapability,
    SupportStatus,
    capability,
    config_class,
    platform_name,
    support_status,
)
from datahub.ingestion.api.source import (
    CapabilityReport,
    TestableSource,
    TestConnectionReport,
)
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.source.redshift.common import get_db_name
from datahub.ingestion.source.redshift.config import RedshiftConfig
from datahub.ingestion.source.redshift.lineage import LineageExtractor
from datahub.ingestion.source.redshift.profile import RedshiftProfiler
from datahub.ingestion.source.redshift.redshift_schema import (
    RedshiftColumn,
    RedshiftDataDictionary,
    RedshiftSchema,
    RedshiftTable,
    RedshiftView,
)
from datahub.ingestion.source.redshift.report import RedshiftReport
from datahub.ingestion.source.redshift.state import RedshiftCheckpointState
from datahub.ingestion.source.redshift.usage import RedshiftUsageExtractor
from datahub.ingestion.source.sql.sql_common import SqlContainerSubTypes, SqlWorkUnit
from datahub.ingestion.source.sql.sql_types import resolve_postgres_modified_type
from datahub.ingestion.source.sql.sql_utils import (
    add_table_to_schema_container,
    gen_database_containers,
    gen_lineage,
    gen_schema_containers,
    get_dataplatform_instance_aspect,
    get_domain_wu,
)
from datahub.ingestion.source.state.profiling_state_handler import ProfilingHandler
from datahub.ingestion.source.state.redundant_run_skip_handler import (
    RedundantRunSkipHandler,
)
from datahub.ingestion.source.state.stale_entity_removal_handler import (
    StaleEntityRemovalHandler,
)
from datahub.ingestion.source.state.stateful_ingestion_base import (
    StatefulIngestionSourceBase,
)
from datahub.metadata.com.linkedin.pegasus2avro.common import (
    Status,
    SubTypes,
    TimeStamp,
)
from datahub.metadata.com.linkedin.pegasus2avro.dataset import (
    DatasetProperties,
    ViewProperties,
)

# TRICKY: it's necessary to import the Postgres source because
# that module has some side effects that we care about here.
from datahub.metadata.com.linkedin.pegasus2avro.schema import (
    ArrayType,
    BooleanType,
    BytesType,
    MySqlDDL,
    NullType,
    NumberType,
    RecordType,
    SchemaField,
    SchemaFieldDataType,
    SchemaMetadata,
    StringType,
    TimeType,
)
from datahub.metadata.schema_classes import GlobalTagsClass, TagAssociationClass
from datahub.utilities import memory_footprint
from datahub.utilities.mapping import Constants
from datahub.utilities.perf_timer import PerfTimer
from datahub.utilities.registries.domain_registry import DomainRegistry
from datahub.utilities.source_helpers import (
    auto_stale_entity_removal,
    auto_status_aspect,
)
from datahub.utilities.time import datetime_to_ts_millis

logger: logging.Logger = logging.getLogger(__name__)


@platform_name("Redshift")
@config_class(RedshiftConfig)
@support_status(SupportStatus.CERTIFIED)
@capability(SourceCapability.PLATFORM_INSTANCE, "Enabled by default")
@capability(SourceCapability.DOMAINS, "Supported via the `domain` config field")
@capability(SourceCapability.DATA_PROFILING, "Optionally enabled via configuration")
@capability(SourceCapability.DESCRIPTIONS, "Enabled by default")
@capability(SourceCapability.LINEAGE_COARSE, "Optionally enabled via configuration")
@capability(SourceCapability.USAGE_STATS, "Optionally enabled via configuration")
@capability(SourceCapability.DELETION_DETECTION, "Enabled via stateful ingestion")
class RedshiftSource(StatefulIngestionSourceBase, TestableSource):
    """
    This plugin extracts the following:

    - Metadata for databases, schemas, views and tables
    - Column types associated with each table
    - Also supports PostGIS extensions
    - Table, row, and column statistics via optional SQL profiling
    - Table lineage

    :::tip

    You can also get fine-grained usage statistics for Redshift using the `redshift-usage` source described below.

    :::

    ### Prerequisites

    This source needs to access system tables that require extra permissions.
    To grant these permissions, please alter your datahub Redshift user the following way:
    ```sql
    ALTER USER datahub_user WITH SYSLOG ACCESS UNRESTRICTED;
    GRANT SELECT ON pg_catalog.svv_table_info to datahub_user;
    GRANT SELECT ON pg_catalog.svl_user_info to datahub_user;
    ```
    :::note

    Giving a user unrestricted access to system tables gives the user visibility to data generated by other users. For example, STL_QUERY and STL_QUERYTEXT contain the full text of INSERT, UPDATE, and DELETE statements.

    :::

    ### Lineage

    There are multiple lineage collector implementations as Redshift does not support table lineage out of the box.

    #### stl_scan_based
    The stl_scan based collector uses Redshift's [stl_insert](https://docs.aws.amazon.com/redshift/latest/dg/r_STL_INSERT.html) and [stl_scan](https://docs.aws.amazon.com/redshift/latest/dg/r_STL_SCAN.html) system tables to
    discover lineage between tables.
    Pros:
    - Fast
    - Reliable

    Cons:
    - Does not work with Spectrum/external tables because those scans do not show up in stl_scan table.
    - If a table is depending on a view then the view won't be listed as dependency. Instead the table will be connected with the view's dependencies.

    #### sql_based
    The sql_based based collector uses Redshift's [stl_insert](https://docs.aws.amazon.com/redshift/latest/dg/r_STL_INSERT.html) to discover all the insert queries
    and uses sql parsing to discover the dependecies.

    Pros:
    - Works with Spectrum tables
    - Views are connected properly if a table depends on it

    Cons:
    - Slow.
    - Less reliable as the query parser can fail on certain queries

    #### mixed
    Using both collector above and first applying the sql based and then the stl_scan based one.

    Pros:
    - Works with Spectrum tables
    - Views are connected properly if a table depends on it
    - A bit more reliable than the sql_based one only

    Cons:
    - Slow
    - May be incorrect at times as the query parser can fail on certain queries

    :::note

    The redshift stl redshift tables which are used for getting data lineage only retain approximately two to five days of log history. This means you cannot extract lineage from queries issued outside that window.

    :::

    """

    REDSHIFT_FIELD_TYPE_MAPPINGS: Dict[
        str,
        Type[
            Union[
                ArrayType,
                BytesType,
                BooleanType,
                NumberType,
                RecordType,
                StringType,
                TimeType,
                NullType,
            ]
        ],
    ] = {
        "BYTES": BytesType,
        "BOOL": BooleanType,
        "DECIMAL": NumberType,
        "NUMERIC": NumberType,
        "BIGNUMERIC": NumberType,
        "BIGDECIMAL": NumberType,
        "FLOAT64": NumberType,
        "INT": NumberType,
        "INT64": NumberType,
        "SMALLINT": NumberType,
        "INTEGER": NumberType,
        "BIGINT": NumberType,
        "TINYINT": NumberType,
        "BYTEINT": NumberType,
        "STRING": StringType,
        "TIME": TimeType,
        "TIMESTAMP": TimeType,
        "DATE": TimeType,
        "DATETIME": TimeType,
        "GEOGRAPHY": NullType,
        "JSON": NullType,
        "INTERVAL": NullType,
        "ARRAY": ArrayType,
        "STRUCT": RecordType,
        "CHARACTER VARYING": StringType,
        "CHARACTER": StringType,
        "CHAR": StringType,
        "TIMESTAMP WITHOUT TIME ZONE": TimeType,
    }

    def get_platform_instance_id(self) -> str:
        """
        The source identifier such as the specific source host address required for stateful ingestion.
        Individual subclasses need to override this method appropriately.
        """
        return f"{self.platform}"

    @staticmethod
    def test_connection(config_dict: dict) -> TestConnectionReport:
        test_report = TestConnectionReport()
        try:
            RedshiftConfig.Config.extra = (
                pydantic.Extra.allow
            )  # we are okay with extra fields during this stage
            config = RedshiftConfig.parse_obj(config_dict)
            # source = RedshiftSource(config, report)
            connection: redshift_connector.Connection = (
                RedshiftSource.get_redshift_connection(config)
            )
            cur = connection.cursor()
            try:
                cur.execute("select 1")
                test_report.basic_connectivity = CapabilityReport(capable=True)
            except Exception as e:
                test_report.basic_connectivity = CapabilityReport(
                    capable=False, failure_reason=str(e)
                )
            test_report.capability_report = {}
            try:
                RedshiftDataDictionary.get_schemas(connection, database=config.database)
                test_report.capability_report[
                    SourceCapability.SCHEMA_METADATA
                ] = CapabilityReport(capable=True)
            except Exception as e:
                test_report.capability_report[
                    SourceCapability.SCHEMA_METADATA
                ] = CapabilityReport(capable=False, failure_reason=str(e))

        except Exception as e:
            test_report.basic_connectivity = CapabilityReport(
                capable=False, failure_reason=f"{e}"
            )
        return test_report

    def get_report(self) -> RedshiftReport:
        return self.report

    eskind_to_platform = {1: "glue", 2: "hive", 3: "postgres", 4: "redshift"}

    def __init__(self, config: RedshiftConfig, ctx: PipelineContext):
        super(RedshiftSource, self).__init__(config, ctx)
        self.lineage_extractor: Optional[LineageExtractor] = None
        self.catalog_metadata: Dict = {}
        self.config: RedshiftConfig = config
        self.report: RedshiftReport = RedshiftReport()
        self.platform = "redshift"
        # Create and register the stateful ingestion use-case handler.
        self.stale_entity_removal_handler = StaleEntityRemovalHandler(
            source=self,
            config=self.config,
            state_type_class=RedshiftCheckpointState,
            pipeline_name=self.ctx.pipeline_name,
            run_id=self.ctx.run_id,
        )
        self.domain_registry = None
        if self.config.domain:
            self.domain_registry = DomainRegistry(
                cached_domains=[k for k in self.config.domain], graph=self.ctx.graph
            )

        self.redundant_run_skip_handler = RedundantRunSkipHandler(
            source=self,
            config=self.config,
            pipeline_name=self.ctx.pipeline_name,
            run_id=self.ctx.run_id,
        )

        self.profiling_state_handler: Optional[ProfilingHandler] = None
        if self.config.store_last_profiling_timestamps:
            self.profiling_state_handler = ProfilingHandler(
                source=self,
                config=self.config,
                pipeline_name=self.ctx.pipeline_name,
                run_id=self.ctx.run_id,
            )

        self.db_tables: Dict[str, Dict[str, List[RedshiftTable]]] = {}
        self.db_views: Dict[str, Dict[str, List[RedshiftView]]] = {}
        self.db_schemas: Dict[str, Dict[str, RedshiftSchema]] = {}

    @classmethod
    def create(cls, config_dict, ctx):
        config = RedshiftConfig.parse_obj(config_dict)
        return cls(config, ctx)

    @staticmethod
    def get_redshift_connection(
        config: RedshiftConfig,
    ) -> redshift_connector.Connection:
        client_options = config.extra_client_options
        host, port = config.host_port.split(":")
        return redshift_connector.connect(
            host=host,
            port=int(port),
            user=config.username,
            database=config.database if config.database else "dev",
            password=config.password.get_secret_value() if config.password else None,
            **client_options,
        )

    def process_schema(
        self,
        connection: redshift_connector.Connection,
        database: str,
        schema: RedshiftSchema,
    ) -> Iterable[MetadataWorkUnit]:
        with PerfTimer() as timer:

            schema_workunits = gen_schema_containers(
                schema=schema.name,
                database=database,
                platform=self.platform,
                platform_instance=self.config.platform_instance,
                domain_config=self.config.domain,
                domain_registry=self.domain_registry,
                sub_types=[SqlContainerSubTypes.SCHEMA],
                env=self.config.env,
                report=self.report,
            )

            for wu in schema_workunits:
                self.report.report_workunit(wu)
                yield wu

            schema_columns: Dict[str, Dict[str, List[RedshiftColumn]]] = {}
            schema_columns[schema.name] = RedshiftDataDictionary.get_columns_for_schema(
                conn=connection, schema=schema
            )

            if self.config.include_tables:
                logger.info("process tables")
                if not self.db_tables[schema.database]:
                    return

                if schema.name in self.db_tables[schema.database]:
                    for table in self.db_tables[schema.database][schema.name]:
                        table.columns = (
                            schema_columns[schema.name][table.name]
                            if table.name in schema_columns[schema.name]
                            else []
                        )
                        yield from self._process_table(table, database=database)
                        self.report.table_processed[f"{database}.{schema.name}"] = (
                            self.report.table_processed.get(
                                f"{database}.{schema.name}", 0
                            )
                            + 1
                        )

            if self.config.include_views:
                logger.info("process views")
                if schema.name in self.db_views[schema.database]:
                    for view in self.db_views[schema.database][schema.name]:
                        logger.info(f"View: {view}")
                        view.columns = (
                            schema_columns[schema.name][view.name]
                            if view.name in schema_columns[schema.name]
                            else []
                        )
                        yield from self._process_view(
                            view, get_db_name(self.config), schema
                        )

                        self.report.view_processed[f"{database}.{schema.name}"] = (
                            self.report.view_processed.get(
                                f"{database}.{schema.name}", 0
                            )
                            + 1
                        )

            self.report.metadata_extraction_sec[f"{database}.{schema.name}"] = round(
                timer.elapsed_seconds(), 2
            )

    def _process_table(
        self,
        table: RedshiftTable,
        database: str,
    ) -> Iterable[MetadataWorkUnit]:

        datahub_dataset_name = (
            f"{get_db_name(config=self.config)}.{table.schema}.{table.name}"
        )

        self.report.report_entity_scanned(datahub_dataset_name)

        if not self.config.table_pattern.allowed(datahub_dataset_name):
            self.report.report_dropped(datahub_dataset_name)
            return

        table_workunits = self.gen_table_dataset_workunits(
            table, database=database, dataset_name=datahub_dataset_name
        )
        for wu in table_workunits:
            self.report.report_workunit(wu)
            yield wu

    def _process_view(
        self, table: RedshiftView, database: str, schema: RedshiftSchema
    ) -> Iterable[MetadataWorkUnit]:

        datahub_dataset_name = f"{database}.{table.schema}.{table.name}"

        self.report.report_entity_scanned(datahub_dataset_name)

        if not self.config.table_pattern.allowed(datahub_dataset_name):
            self.report.report_dropped(datahub_dataset_name)
            return

        table_workunits = self.gen_view_dataset_workunits(
            table=table,
            database=database,
            schema=table.schema,
        )

        for wu in table_workunits:
            self.report.report_workunit(wu)
            yield wu

    def gen_table_dataset_workunits(
        self,
        table: RedshiftTable,
        database: str,
        dataset_name: str,
    ) -> Iterable[MetadataWorkUnit]:

        custom_properties = {}

        if table.location:
            custom_properties["location"] = table.location

        if table.input_parameters:
            custom_properties["input_parameters"] = table.input_parameters

        if table.output_parameters:
            custom_properties["output_parameters"] = table.output_parameters

        if table.dist_style:
            custom_properties["dist_style"] = table.dist_style

        if table.parameters:
            custom_properties["parameters"] = table.parameters

        if table.serde_parameters:
            custom_properties["serde_parameters"] = table.serde_parameters

        yield from self.gen_dataset_workunits(
            table=table,
            database=database,
            schema=table.schema,
            sub_type=table.type,
            tags_to_add=[],
            custom_properties=custom_properties,
        )

    # TODO: Remove to common?
    def gen_view_dataset_workunits(
        self,
        table: RedshiftView,
        database: str,
        schema: str,
    ) -> Iterable[MetadataWorkUnit]:

        view = cast(RedshiftView, table)

        yield from self.gen_dataset_workunits(
            table=table,
            database=get_db_name(self.config),
            schema=table.schema,
            sub_type=table.type,
            tags_to_add=[],
            custom_properties={},
        )

        view_definition_string = view.ddl
        view_properties_aspect = ViewProperties(
            materialized=table.type == "VIEW_MATERIALIZED",
            viewLanguage="SQL",
            viewLogic=view_definition_string,
        )
        datahub_dataset_name = f"{database}.{schema}.{table.name}"
        dataset_urn = make_dataset_urn_with_platform_instance(
            platform=self.platform,
            name=datahub_dataset_name,
            platform_instance=self.config.platform_instance,
        )
        wu = wrap_aspect_as_workunit(
            "dataset",
            dataset_urn,
            "viewProperties",
            view_properties_aspect,
        )
        yield wu
        self.report.report_workunit(wu)

    # TODO: Remove to common?
    def gen_schema_fields(self, columns: List[RedshiftColumn]) -> List[SchemaField]:
        schema_fields: List[SchemaField] = []

        for col in columns:
            tags: List[TagAssociationClass] = []
            if col.dist_key:
                tags.append(TagAssociationClass(make_tag_urn(Constants.TAG_DIST_KEY)))

            if col.sort_key:
                tags.append(TagAssociationClass(make_tag_urn(Constants.TAG_SORT_KEY)))

            data_type = self.REDSHIFT_FIELD_TYPE_MAPPINGS.get(col.data_type)
            # We have to remove the precision part to properly parse it
            if data_type is None:
                # attempt Postgres modified type
                data_type = resolve_postgres_modified_type(col.data_type.lower())

            field = SchemaField(
                fieldPath=col.name,
                type=SchemaFieldDataType(data_type() if data_type else NullType()),
                # NOTE: nativeDataType will not be in sync with older connector
                nativeDataType=col.data_type,
                description=col.comment,
                nullable=col.is_nullable,
                globalTags=GlobalTagsClass(tags=tags),
            )
            schema_fields.append(field)
        return schema_fields

    # TODO: Move to common?
    def gen_schema_metadata(
        self,
        dataset_urn: str,
        table: Union[RedshiftTable, RedshiftView],
        dataset_name: str,
    ) -> MetadataWorkUnit:

        schema_metadata = SchemaMetadata(
            schemaName=dataset_name,
            platform=make_data_platform_urn(self.platform),
            version=0,
            hash="",
            platformSchema=MySqlDDL(tableSchema=""),
            fields=self.gen_schema_fields(table.columns),
        )
        wu = wrap_aspect_as_workunit(
            "dataset", dataset_urn, "schemaMetadata", schema_metadata
        )
        self.report.report_workunit(wu)
        return wu

    # TODO: Move to common
    def gen_dataset_workunits(
        self,
        table: Union[RedshiftTable, RedshiftView],
        database: str,
        schema: str,
        sub_type: str,
        tags_to_add: Optional[List[str]] = None,
        custom_properties: Optional[Dict[str, str]] = None,
    ) -> Iterable[MetadataWorkUnit]:
        datahub_dataset_name = f"{database}.{schema}.{table.name}"
        dataset_urn = make_dataset_urn_with_platform_instance(
            platform=self.platform,
            name=datahub_dataset_name,
            platform_instance=self.config.platform_instance,
        )
        status = Status(removed=False)
        wu = wrap_aspect_as_workunit("dataset", dataset_urn, "status", status)
        yield wu
        self.report.report_workunit(wu)

        yield self.gen_schema_metadata(dataset_urn, table, str(datahub_dataset_name))

        dataset_properties = DatasetProperties(
            name=table.name,
            created=TimeStamp(time=int(table.created.timestamp() * 1000))
            if table.created
            else None,
            lastModified=TimeStamp(time=int(table.last_altered.timestamp() * 1000))
            if table.last_altered
            else TimeStamp(time=int(table.created.timestamp() * 1000))
            if table.created
            else None,
            description=table.description,
            qualifiedName=str(datahub_dataset_name),
        )

        if custom_properties:
            dataset_properties.customProperties = custom_properties

        wu = wrap_aspect_as_workunit(
            "dataset", dataset_urn, "datasetProperties", dataset_properties
        )
        yield wu
        self.report.report_workunit(wu)

        # TODO: Check if needed
        # if tags_to_add:
        #    yield gen_tags_aspect_workunit(dataset_urn, tags_to_add)

        yield from add_table_to_schema_container(
            dataset_urn,
            database,
            schema,
            platform_instance=self.config.platform_instance,
            platform=self.platform,
            env=self.config.env,
            report=self.report,
        )
        dpi_aspect = get_dataplatform_instance_aspect(
            dataset_urn=dataset_urn,
            platform=self.platform,
            platform_instance=self.config.platform_instance,
        )
        if dpi_aspect:
            self.report.report_workunit(dpi_aspect)
            yield dpi_aspect

        subTypes = SubTypes(typeNames=[sub_type])
        wu = wrap_aspect_as_workunit("dataset", dataset_urn, "subTypes", subTypes)
        yield wu
        self.report.report_workunit(wu)

        if self.domain_registry:
            yield from get_domain_wu(
                dataset_name=str(datahub_dataset_name),
                entity_urn=dataset_urn,
                entity_type="dataset",
                domain_registry=self.domain_registry,
                domain_config=self.config.domain,
                report=self.report,
            )

    def get_workunits_internal(self) -> Iterable[Union[MetadataWorkUnit, SqlWorkUnit]]:
        connection = RedshiftSource.get_redshift_connection(self.config)
        database = get_db_name(self.config)
        logger.info(f"Processing db {self.config.database} with name {database}")
        # self.add_config_to_report()
        self.db_tables[database] = defaultdict()
        self.db_views[database] = defaultdict()

        yield from gen_database_containers(
            database=database,
            domain_config=self.config.domain,
            domain_registry=self.domain_registry,
            report=self.report,
            platform_instance=self.config.platform_instance,
            platform=self.platform,
            env=self.config.env,
            sub_types=[SqlContainerSubTypes.DATABASE],
        )
        self.cache_tables_and_views(connection, database)

        self.report.tables_in_mem_size[database] = humanfriendly.format_size(
            memory_footprint.total_size(self.db_tables)
        )
        self.report.views_in_mem_size[database] = humanfriendly.format_size(
            memory_footprint.total_size(self.db_views)
        )

        for schema in RedshiftDataDictionary.get_schemas(
            conn=connection, database=database
        ):
            logger.info(f"Schema: {database}.{schema.name}")
            if not self.config.schema_pattern.allowed(schema.name):
                self.report.report_dropped(f"{database}.{schema.name}")
                continue
            if database not in self.db_schemas:
                self.db_schemas[database] = {}
            self.db_schemas[database][schema.name] = schema
            yield from self.process_schema(connection, database, schema)

        all_tables = self.get_all_tables()

        if (
            self.config.store_last_lineage_extraction_timestamp
            or self.config.store_last_usage_extraction_timestamp
        ):
            # Update the checkpoint state for this run.
            self.redundant_run_skip_handler.update_state(
                start_time_millis=datetime_to_ts_millis(self.config.start_time),
                end_time_millis=datetime_to_ts_millis(self.config.end_time),
            )

        if self.config.include_table_lineage or self.config.include_copy_lineage:
            yield from self.extract_lineage(
                connection=connection, all_tables=all_tables, database=database
            )

        if self.config.include_usage_statistics:
            yield from self.extract_usage(
                connection=connection, all_tables=all_tables, database=database
            )

        if self.config.profiling.enabled:
            profiler = RedshiftProfiler(
                config=self.config,
                report=self.report,
                state_handler=self.profiling_state_handler,
            )
            yield from profiler.get_workunits(self.db_tables)

    def cache_tables_and_views(self, connection, database):
        tables, views = RedshiftDataDictionary.get_tables_and_views(conn=connection)
        for schema in tables:
            if self.config.schema_pattern.allowed(f"{database}.{schema}"):
                self.db_tables[database][schema] = []
                for table in tables[schema]:
                    if self.config.table_pattern.allowed(
                        f"{database}.{schema}.{table.name}"
                    ):
                        self.db_tables[database][schema].append(table)
        for schema in views:
            if self.config.schema_pattern.allowed(f"{database}.{schema}"):
                self.db_views[database][schema] = []
                for table in views[schema]:
                    if self.config.view_pattern.allowed(
                        f"{database}.{schema}.{table.name}"
                    ):
                        self.db_views[database][schema].append(table)

    def get_all_tables(
        self,
    ) -> Dict[str, Dict[str, List[Union[RedshiftView, RedshiftTable]]]]:
        all_tables: Dict[str, Dict[str, List[Union[RedshiftView, RedshiftTable]]]] = {
            **self.db_tables,
        }  # type: ignore
        for db in self.db_views.keys():
            if db in all_tables:
                for schema in self.db_views[db].keys():
                    if schema in all_tables[db]:
                        all_tables[db][schema].extend(self.db_views[db][schema])
                    else:
                        all_tables[db][schema] = self.db_views[db][schema]
            else:
                all_tables[db] = self.db_views[db]
        return all_tables

    def extract_usage(
        self,
        connection: redshift_connector.Connection,
        database: str,
        all_tables: Dict[str, Dict[str, List[Union[RedshiftView, RedshiftTable]]]],
    ) -> Iterable[MetadataWorkUnit]:
        if (
            self.config.store_last_lineage_extraction_timestamp
            and self.redundant_run_skip_handler.should_skip_this_run(
                cur_start_time_millis=datetime_to_ts_millis(self.config.start_time)
            )
        ):
            # Skip this run
            self.report.report_warning(
                "usage-extraction",
                f"Skip this run as there was a run later than the current start time: {self.config.start_time}",
            )
            return

        with PerfTimer() as timer:
            usage_extractor = RedshiftUsageExtractor(
                config=self.config,
                connection=connection,
                report=self.report,
            )
            yield from usage_extractor.generate_usage(all_tables=all_tables)

            self.report.usage_extraction_sec[database] = round(
                timer.elapsed_seconds(), 2
            )

    def extract_lineage(
        self,
        connection: redshift_connector.Connection,
        database: str,
        all_tables: Dict[str, Dict[str, List[Union[RedshiftView, RedshiftTable]]]],
    ) -> Iterable[MetadataWorkUnit]:
        if (
            self.config.store_last_lineage_extraction_timestamp
            and self.redundant_run_skip_handler.should_skip_this_run(
                cur_start_time_millis=datetime_to_ts_millis(self.config.start_time)
            )
        ):
            # Skip this run
            self.report.report_warning(
                "lineage-extraction",
                f"Skip this run as there was a run later than the current start time: {self.config.start_time}",
            )
            return

        self.lineage_extractor = LineageExtractor(
            config=self.config,
            report=self.report,
        )

        with PerfTimer() as timer:
            self.lineage_extractor.populate_lineage(
                connection=connection, all_tables=all_tables
            )

            self.report.lineage_extraction_sec[f"{database}"] = round(
                timer.elapsed_seconds(), 2
            )
            wus = self.generate_lineage(database)
            for wu in wus:
                yield wu
                self.report.report_workunit(wu)

    def generate_lineage(self, database: str) -> Iterable[MetadataWorkUnit]:
        assert self.lineage_extractor

        logger.info(f"Generate lineage for {database}")
        for schema in self.db_tables[database]:
            for table in self.db_tables[database][schema]:
                if (
                    database not in self.db_schemas
                    or schema not in self.db_schemas[database]
                ):
                    continue
                datahub_dataset_name = f"{database}.{table.schema}.{table.name}"
                dataset_urn = make_dataset_urn_with_platform_instance(
                    platform=self.platform,
                    name=datahub_dataset_name,
                    platform_instance=self.config.platform_instance,
                )

                lineage_info = self.lineage_extractor.get_lineage(
                    table,
                    dataset_urn,
                    self.db_schemas[database][schema],
                )
                if lineage_info:
                    for wu in gen_lineage(
                        dataset_urn, lineage_info, self.config.incremental_lineage
                    ):
                        self.report.report_workunit(wu)
                        yield wu

        for schema in self.db_views[database]:
            for view in self.db_views[database][schema]:
                datahub_dataset_name = f"{database}.{view.schema}.{view.name}"
                dataset_urn = make_dataset_urn_with_platform_instance(
                    self.platform,
                    datahub_dataset_name,
                    self.config.platform_instance,
                    env=self.config.env,
                )
                lineage_info = self.lineage_extractor.get_lineage(
                    view,
                    dataset_urn,
                    self.db_schemas[database][schema],
                )
                if lineage_info:
                    for wu in gen_lineage(
                        dataset_urn, lineage_info, self.config.incremental_lineage
                    ):
                        self.report.report_workunit(wu)
                        yield wu

    def get_workunits(self) -> Iterable[MetadataWorkUnit]:
        return auto_stale_entity_removal(
            self.stale_entity_removal_handler,
            auto_status_aspect(self.get_workunits_internal()),
        )
