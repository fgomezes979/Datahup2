import functools
import json
import uuid
from dataclasses import dataclass
from textwrap import dedent
from typing import Any, Dict, Iterable, List, Optional, Union

import sqlalchemy
import trino
from packaging import version
from pydantic.fields import Field
from sqlalchemy import exc, sql
from sqlalchemy.engine import reflection
from sqlalchemy.engine.reflection import Inspector
from sqlalchemy.sql import sqltypes
from sqlalchemy.types import TypeEngine
from trino.exceptions import TrinoQueryError
from trino.sqlalchemy import datatype
from trino.sqlalchemy.dialect import TrinoDialect

from datahub.configuration.source_common import (
    EnvConfigMixin,
    PlatformInstanceConfigMixin,
)
from datahub.emitter.mce_builder import make_dataset_urn_with_platform_instance
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
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.extractor import schema_util
from datahub.ingestion.source.sql.sql_common import (
    SQLAlchemySource,
    SqlWorkUnit,
    register_custom_type,
)
from datahub.ingestion.source.sql.sql_config import (
    BasicSQLAlchemyConfig,
    SQLCommonConfig,
)
from datahub.metadata.com.linkedin.pegasus2avro.common import Siblings
from datahub.metadata.com.linkedin.pegasus2avro.schema import (
    MapTypeClass,
    NumberTypeClass,
    RecordTypeClass,
    SchemaField,
)

register_custom_type(datatype.ROW, RecordTypeClass)
register_custom_type(datatype.MAP, MapTypeClass)
register_custom_type(datatype.DOUBLE, NumberTypeClass)


@dataclass
class PlatformDetail:
    platform_name: str
    is_three_tier: bool


KNOWN_CONNECTOR_PLATFORM_MAPPING = {
    "hive": PlatformDetail("hive", False),
    "postgresql": PlatformDetail("postgres", True),
    "mysql": PlatformDetail("mysql", False),
    "redshift": PlatformDetail("redshift", True),
    "bigquery": PlatformDetail("bigquery", True),
}

# Type JSON was introduced in trino sqlalchemy dialect in version 0.317.0
if version.parse(trino.__version__) >= version.parse("0.317.0"):
    register_custom_type(datatype.JSON, RecordTypeClass)


# Read only table names and skip view names, as view names will also be returned
# from get_view_names
@reflection.cache  # type: ignore
def get_table_names(self, connection, schema: str = None, **kw):  # type: ignore
    schema = schema or self._get_default_schema_name(connection)
    if schema is None:
        raise exc.NoSuchTableError("schema is required")
    query = dedent(
        """
        SELECT "table_name"
        FROM "information_schema"."tables"
        WHERE "table_schema" = :schema and "table_type" != 'VIEW'
    """
    ).strip()
    res = connection.execute(sql.text(query), schema=schema)
    return [row.table_name for row in res]


# Include all table properties instead of only "comment" property
@reflection.cache  # type: ignore
def get_table_comment(self, connection, table_name: str, schema: str = None, **kw):  # type: ignore
    try:
        properties_table = self._get_full_table(f"{table_name}$properties", schema)
        query = f"SELECT * FROM {properties_table}"
        row = connection.execute(sql.text(query)).fetchone()

        # Generate properties dictionary.
        properties = {}
        if row:
            for col_name, col_value in row.items():
                if col_value is not None:
                    properties[col_name] = col_value

        return {"text": properties.get("comment", None), "properties": properties}
    # Fallback to default trino-sqlalchemy behaviour if `$properties` table doesn't exist
    except TrinoQueryError:
        return self.get_table_comment_default(connection, table_name, schema)
    # Exception raised when using Starburst Delta Connector that falls back to a Hive Catalog
    except exc.ProgrammingError as e:
        if isinstance(e.orig, TrinoQueryError):
            return self.get_table_comment_default(connection, table_name, schema)
        raise
    except Exception:
        return {}


# Include column comment, original trino datatype as full_type
@reflection.cache  # type: ignore
def _get_columns(self, connection, table_name, schema: str = None, **kw):  # type: ignore
    schema = schema or self._get_default_schema_name(connection)
    query = dedent(
        """
        SELECT
            "column_name",
            "data_type",
            "column_default",
            UPPER("is_nullable") AS "is_nullable",
            "comment"
        FROM "information_schema"."columns"
        WHERE "table_schema" = :schema
            AND "table_name" = :table
        ORDER BY "ordinal_position" ASC
    """
    ).strip()
    res = connection.execute(sql.text(query), schema=schema, table=table_name)
    columns = []
    for record in res:
        column = dict(
            name=record.column_name,
            type=datatype.parse_sqltype(record.data_type),
            nullable=record.is_nullable == "YES",
            default=record.column_default,
            comment=record.comment,
        )
        columns.append(column)
    return columns


TrinoDialect.get_table_comment_default = TrinoDialect.get_table_comment
TrinoDialect.get_table_names = get_table_names
TrinoDialect.get_table_comment = get_table_comment
TrinoDialect._get_columns = _get_columns


@functools.lru_cache()
def get_catalog_connector_name(
    catalog_name: str, inspector: Inspector
) -> Optional[str]:
    if inspector.engine:
        query = dedent(
            """
            SELECT *
            FROM "system"."metadata"."catalogs"
        """
        ).strip()
        res = inspector.engine.execute(sql.text(query))
        catalog_connector_dict = {row.catalog_name: row.connector_name for row in res}
        return catalog_connector_dict.get(catalog_name)
    return None


class ConnectorDetail(PlatformInstanceConfigMixin, EnvConfigMixin):
    connector_database: Optional[str] = Field(default=None, description="")


class TrinoConfig(BasicSQLAlchemyConfig):
    # defaults
    scheme: str = Field(default="trino", description="", hidden_from_docs=True)

    catalog_to_connector_details: Dict[str, ConnectorDetail] = Field(
        default={},
        description="A mapping of trino catalog to its connector details like connector database, platform instance."
        "This configuration is used to ingest siblings of datasets. Use catalog name as key."
        "For three tier connectors like postgresql, connector database is required.",
    )

    ingest_siblings: bool = Field(
        default=True, description="Whether siblings of datasets should be ingested"
    )

    def get_identifier(self: BasicSQLAlchemyConfig, schema: str, table: str) -> str:
        identifier = f"{schema}.{table}"
        if self.database:  # TODO: this should be required field
            identifier = f"{self.database}.{identifier}"
        return identifier


@platform_name("Trino", doc_order=1)
@config_class(TrinoConfig)
@support_status(SupportStatus.CERTIFIED)
@capability(SourceCapability.DOMAINS, "Supported via the `domain` config field")
@capability(SourceCapability.DATA_PROFILING, "Optionally enabled via configuration")
class TrinoSource(SQLAlchemySource):
    """

    This plugin extracts the following:

    - Metadata for databases, schemas, and tables
    - Column types and schema associated with each table
    - Table, row, and column statistics via optional SQL profiling

    """

    config: TrinoConfig

    def __init__(
        self, config: TrinoConfig, ctx: PipelineContext, platform: str = "trino"
    ):
        super().__init__(config, ctx, platform)

    def get_db_name(self, inspector: Inspector) -> str:
        if self.config.database:
            return f"{self.config.database}"
        else:
            return super().get_db_name(inspector)

    def _get_sibling_urn(
        self,
        dataset_name: str,
        inspector: Inspector,
        schema: str,
        table: str,
    ) -> Optional[str]:
        catalog_name = dataset_name.split(".")[0]
        connector_platform_details: Optional[PlatformDetail] = None

        connector_name = get_catalog_connector_name(catalog_name, inspector)
        if connector_name:
            connector_platform_details = KNOWN_CONNECTOR_PLATFORM_MAPPING.get(
                connector_name
            )

        connector_details = self.config.catalog_to_connector_details.get(
            catalog_name, ConnectorDetail()
        )

        if connector_platform_details:
            if not connector_platform_details.is_three_tier:  # connector is two tier
                return make_dataset_urn_with_platform_instance(
                    platform=connector_platform_details.platform_name,
                    name=f"{schema}.{table}",
                    platform_instance=connector_details.platform_instance,
                    env=connector_details.env,
                )
            elif connector_details.connector_database:  # connector is three tier
                return make_dataset_urn_with_platform_instance(
                    platform=connector_platform_details.platform_name,
                    name=f"{connector_details.connector_database}.{schema}.{table}",
                    platform_instance=connector_details.platform_instance,
                    env=connector_details.env,
                )

        return None

    def get_sibling_workunit(
        self,
        dataset_name: str,
        inspector: Inspector,
        schema: str,
        table: str,
    ) -> Optional[MetadataWorkUnit]:
        dataset_urn = make_dataset_urn_with_platform_instance(
            self.platform,
            dataset_name,
            self.config.platform_instance,
            self.config.env,
        )
        sibling_urn = self._get_sibling_urn(dataset_name, inspector, schema, table)
        if self.config.ingest_siblings and sibling_urn:
            return MetadataChangeProposalWrapper(
                entityUrn=dataset_urn,
                aspect=Siblings(primary=False, siblings=[sibling_urn]),
            ).as_workunit()
        return None

    def _process_table(
        self,
        dataset_name: str,
        inspector: Inspector,
        schema: str,
        table: str,
        sql_config: SQLCommonConfig,
    ) -> Iterable[Union[SqlWorkUnit, MetadataWorkUnit]]:
        yield from super()._process_table(
            dataset_name, inspector, schema, table, sql_config
        )
        sibling_workunit = self.get_sibling_workunit(
            dataset_name, inspector, schema, table
        )
        if sibling_workunit:
            yield sibling_workunit

    def _process_view(
        self,
        dataset_name: str,
        inspector: Inspector,
        schema: str,
        view: str,
        sql_config: SQLCommonConfig,
    ) -> Iterable[Union[SqlWorkUnit, MetadataWorkUnit]]:
        yield from super()._process_view(
            dataset_name, inspector, schema, view, sql_config
        )
        sibling_workunit = self.get_sibling_workunit(
            dataset_name, inspector, schema, view
        )
        if sibling_workunit:
            yield sibling_workunit

    @classmethod
    def create(cls, config_dict, ctx):
        config = TrinoConfig.parse_obj(config_dict)
        return cls(config, ctx)

    def get_schema_fields_for_column(
        self,
        dataset_name: str,
        column: dict,
        pk_constraints: Optional[dict] = None,
        tags: Optional[List[str]] = None,
    ) -> List[SchemaField]:
        fields = super().get_schema_fields_for_column(
            dataset_name, column, pk_constraints
        )

        if isinstance(column["type"], (datatype.ROW, sqltypes.ARRAY, datatype.MAP)):
            assert len(fields) == 1
            field = fields[0]
            # Get avro schema for subfields along with parent complex field
            avro_schema = self.get_avro_schema_from_data_type(
                column["type"], column["name"]
            )

            newfields = schema_util.avro_schema_to_mce_fields(
                json.dumps(avro_schema), default_nullable=True
            )

            # First field is the parent complex field
            newfields[0].nullable = field.nullable
            newfields[0].description = field.description
            newfields[0].isPartOfKey = field.isPartOfKey
            return newfields

        return fields

    def get_avro_schema_from_data_type(
        self, column_type: TypeEngine, column_name: str
    ) -> Dict[str, Any]:
        # Below Record structure represents the dataset level
        # Inner fields represent the complex field (struct/array/map/union)
        return {
            "type": "record",
            "name": "__struct_",
            "fields": [{"name": column_name, "type": _parse_datatype(column_type)}],
        }


_all_atomic_types = {
    sqltypes.BOOLEAN: "boolean",
    sqltypes.SMALLINT: "int",
    sqltypes.INTEGER: "int",
    sqltypes.BIGINT: "long",
    sqltypes.REAL: "float",
    datatype.DOUBLE: "double",  # type: ignore
    sqltypes.VARCHAR: "string",
    sqltypes.CHAR: "string",
    sqltypes.JSON: "record",
}


def _parse_datatype(s):
    if isinstance(s, sqlalchemy.types.ARRAY):
        return {
            "type": "array",
            "items": _parse_datatype(s.item_type),
            "native_data_type": repr(s),
        }
    elif isinstance(s, datatype.MAP):
        kt = _parse_datatype(s.key_type)
        vt = _parse_datatype(s.value_type)
        # keys are assumed to be strings in avro map
        return {
            "type": "map",
            "values": vt,
            "native_data_type": repr(s),
            "key_type": kt,
            "key_native_data_type": repr(s.key_type),
        }
    elif isinstance(s, datatype.ROW):
        return _parse_struct_fields(s.attr_types)
    else:
        return _parse_basic_datatype(s)


def _parse_struct_fields(parts):
    fields = []
    for name_and_type in parts:
        field_name = name_and_type[0].strip()
        field_type = _parse_datatype(name_and_type[1])
        fields.append({"name": field_name, "type": field_type})
    return {
        "type": "record",
        "name": "__struct_{}".format(str(uuid.uuid4()).replace("-", "")),
        "fields": fields,
        "native_data_type": "ROW({})".format(parts),
    }


def _parse_basic_datatype(s):
    for sql_type in _all_atomic_types.keys():
        if isinstance(s, sql_type):
            return {
                "type": _all_atomic_types[sql_type],
                "native_data_type": repr(s),
                "_nullable": True,
            }

    if isinstance(s, sqlalchemy.types.DECIMAL):
        return {
            "type": "bytes",
            "logicalType": "decimal",
            "precision": s.precision,  # type: ignore
            "scale": s.scale,  # type: ignore
            "native_data_type": repr(s),
            "_nullable": True,
        }
    elif isinstance(s, sqlalchemy.types.Date):
        return {
            "type": "int",
            "logicalType": "date",
            "native_data_type": repr(s),
            "_nullable": True,
        }
    elif isinstance(s, (sqlalchemy.types.DATETIME, sqlalchemy.types.TIMESTAMP)):
        return {
            "type": "int",
            "logicalType": "timestamp-millis",
            "native_data_type": repr(s),
            "_nullable": True,
        }

    return {"type": "null", "native_data_type": repr(s)}
