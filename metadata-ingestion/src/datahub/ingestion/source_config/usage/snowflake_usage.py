import logging
from typing import Optional

import pydantic

from datahub.ingestion.source.usage.usage_common import BaseUsageConfig

logger = logging.getLogger(__name__)


class SnowflakeUsageConfig(BaseUsageConfig):
    email_domain: Optional[str] = pydantic.Field(
        default=None,
        description="Email domain of your organisation so users can be displayed on UI appropriately.",
    )
    tag_pattern: AllowDenyPattern = pydantic.Field(
        default=AllowDenyPattern.allow_all(),
        description="List of regex patterns for tags to include in ingestion.",
    )
    apply_view_usage_to_tables: bool = pydantic.Field(
        default=False,
        description="Allow/deny patterns for views in snowflake dataset names.",
    )
