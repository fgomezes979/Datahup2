import datetime
import os
from typing import List, Literal, Optional

import certifi
import pydantic
from pydantic import Field, validator

from datahub.configuration.common import AllowDenyPattern, ConfigModel
from datahub.configuration.source_common import (
    EnvConfigMixin,
    PlatformInstanceConfigMixin,
)
from datahub.ingestion.source.ge_profiling_config import GEProfilingConfig
from datahub.ingestion.source.state.stale_entity_removal_handler import (
    StatefulStaleMetadataRemovalConfig,
)
from datahub.ingestion.source.state.stateful_ingestion_base import (
    StatefulIngestionConfigBase,
)
from datahub.ingestion.source.usage.usage_common import BaseUsageConfig
from datahub.ingestion.source_config.operation_config import is_profiling_enabled


class DremioConnectionConfig(ConfigModel):
    hostname: Optional[str] = Field(
        default=None,
        description="Hostname or IP Address of the Dremio server",
    )

    port: int = Field(
        default=9047,
        description="Port of the Dremio REST API",
    )

    username: Optional[str] = Field(
        default=None,
        description="Dremio username",
    )

    authentication_method: Optional[str] = Field(
        default="PAT",
        description="Authentication method: 'password' or 'PAT' (Personal Access Token)",
    )

    password: Optional[str] = Field(
        default=None,
        description="Dremio password or Personal Access Token",
    )

    tls: bool = Field(
        default=True,
        description="Whether the Dremio REST API port is encrypted",
    )

    disable_certificate_verification: Optional[bool] = Field(
        default=False,
        description="Disable TLS certificate verification",
    )

    path_to_certificates: str = Field(
        default=certifi.where(),
        description="Path to SSL certificates",
    )

    is_dremio_cloud: bool = Field(
        default=False,
        description="Whether this is a Dremio Cloud instance",
    )

    dremio_cloud_region: Literal["US", "EU"] = Field(
        default="US",
        description="Dremio Cloud region ('US' or 'EU')",
    )

    dremio_cloud_project_id: Optional[str] = Field(
        default=None,
        description="ID of Dremio Cloud Project. Found in Project Settings in the Dremio Cloud UI",
    )

    @validator("authentication_method")
    def validate_auth_method(cls, value):
        allowed_methods = ["password", "PAT"]
        if value not in allowed_methods:
            raise ValueError(
                f"authentication_method must be one of {allowed_methods}",
            )
        return value

    @validator("password")
    def validate_password(cls, value, values):
        if values.get("authentication_method") == "PAT" and not value:
            raise ValueError(
                "Password (Personal Access Token) is required when using PAT authentication",
            )
        return value


class ProfileConfig(GEProfilingConfig):

    query_timeout: int = Field(
        default=300, description="Time before cancelling Dremio profiling query"
    )

    row_count: bool = True
    column_count: bool = True
    sample_values: bool = True

    # Below Configs inherited from GEProfilingConfig
    # but not used in Dremio so we hide them from docs.
    include_field_median_value: bool = Field(
        default=False,
        hidden_from_docs=True,
        description="Median causes a number of issues in Dremio.",
    )
    partition_profiling_enabled: bool = Field(default=True, hidden_from_docs=True)
    profile_table_row_count_estimate_only: bool = Field(
        default=False, hidden_from_docs=True
    )
    query_combiner_enabled: bool = Field(default=True, hidden_from_docs=True)
    max_number_of_fields_to_profile: Optional[pydantic.PositiveInt] = Field(
        default=None, hidden_from_docs=True
    )
    profile_if_updated_since_days: Optional[pydantic.PositiveFloat] = Field(
        default=None, hidden_from_docs=True
    )
    profile_table_size_limit: Optional[int] = Field(
        default=5,
        description="Profile tables only if their size is less then specified GBs. If set to `null`, no limit on the size of tables to profile. Supported only in `snowflake` and `BigQuery`",
        hidden_from_docs=True,
    )

    profile_table_row_limit: Optional[int] = Field(
        default=5000000,
        hidden_from_docs=True,
        description="Profile tables only if their row count is less then specified count. If set to `null`, no limit on the row count of tables to profile. Supported only in `snowflake` and `BigQuery`",
    )

    partition_datetime: Optional[datetime.datetime] = Field(
        default=None,
        hidden_from_docs=True,
        description="If specified, profile only the partition which matches this datetime. "
        "If not specified, profile the latest partition. Only Bigquery supports this.",
    )
    use_sampling: bool = Field(
        default=True,
        hidden_from_docs=True,
        description="Whether to profile column level stats on sample of table. Only BigQuery and Snowflake support this. "
        "If enabled, profiling is done on rows sampled from table. Sampling is not done for smaller tables. ",
    )

    sample_size: int = Field(
        default=10000,
        hidden_from_docs=True,
        description="Number of rows to be sampled from table for column level profiling."
        "Applicable only if `use_sampling` is set to True.",
    )
    profile_external_tables: bool = Field(
        default=False,
        hidden_from_docs=True,
        description="Whether to profile external tables. Only Snowflake and Redshift supports this.",
    )

    tags_to_ignore_sampling: Optional[List[str]] = pydantic.Field(
        default=None,
        hidden_from_docs=True,
        description=(
            "Fixed list of tags to ignore sampling."
            " If not specified, tables will be sampled based on `use_sampling`."
        ),
    )


class DremioSourceMapping(EnvConfigMixin, PlatformInstanceConfigMixin, ConfigModel):
    platform: str = Field(
        description="Source connection made by Dremio (e.g. S3, Snowflake)",
    )
    source_name: str = Field(
        description="Alias of platform in Dremio connection",
    )


class DremioSourceConfig(
    DremioConnectionConfig,
    StatefulIngestionConfigBase,
    EnvConfigMixin,
    PlatformInstanceConfigMixin,
):

    domain: Optional[str] = Field(
        default=None,
        description="Domain for all source objects.",
    )

    source_mappings: Optional[List[DremioSourceMapping]] = Field(
        default=None,
        description="Mappings from Dremio sources to DataHub platforms and datasets.",
    )

    # Entity Filters
    schema_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="Regex patterns for schemas to filter",
    )

    dataset_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="Regex patterns for tables and views to filter in ingestion. Specify regex to match the entire table name in dremio.schema.table format. e.g. to match all tables starting with customer in Customer database and public schema, use the regex 'dremio.public.customer.*'",
    )

    usage: BaseUsageConfig = Field(
        description="The usage config to use when generating usage statistics",
        default=BaseUsageConfig(),
    )

    stateful_ingestion: Optional[StatefulStaleMetadataRemovalConfig] = None

    # Profiling
    profile_pattern: AllowDenyPattern = Field(
        default=AllowDenyPattern.allow_all(),
        description="Regex patterns for tables to profile",
    )
    profiling: ProfileConfig = Field(
        default=ProfileConfig(),
        description="Configuration for profiling",
    )

    def is_profiling_enabled(self) -> bool:
        return self.profiling.enabled and is_profiling_enabled(
            self.profiling.operation_config
        )

    # Advanced Configs
    max_workers: int = Field(
        default=5 * (os.cpu_count() or 4),
        description="Number of worker threads to use for parallel processing",
    )

    include_query_lineage: bool = Field(
        default=False,
        description="Whether to include query-based lineage information.",
    )
