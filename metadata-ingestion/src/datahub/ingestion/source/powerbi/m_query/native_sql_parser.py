import logging
from typing import List, Optional

import sqlparse

from datahub.ingestion.api.common import PipelineContext
from datahub.utilities.sqlglot_lineage import (
    SchemaResolver,
    SqlParsingResult,
    sqlglot_lineage,
)

SPECIAL_CHARACTERS = ["#(lf)", "(lf)"]

logger = logging.getLogger()


def remove_special_characters(native_query: str) -> str:
    for char in SPECIAL_CHARACTERS:
        native_query = native_query.replace(char, " ")

    return native_query


def get_tables(native_query: str) -> List[str]:
    native_query = remove_special_characters(native_query)
    logger.debug(f"Processing query = {native_query}")
    tables: List[str] = []
    parsed = sqlparse.parse(native_query)[0]
    tokens: List[sqlparse.sql.Token] = list(parsed.tokens)
    length: int = len(tokens)
    from_index: int = -1
    for index, token in enumerate(tokens):
        logger.debug(f"{token.value}={token.ttype}")
        if (
            token.value.lower().strip() == "from"
            and str(token.ttype) == "Token.Keyword"
        ):
            from_index = index + 1
            break

    # Collect all identifier after FROM clause till we reach to the end or WHERE clause encounter
    while (
        from_index < length
        and isinstance(tokens[from_index], sqlparse.sql.Where) is not True
    ):
        logger.debug(f"{tokens[from_index].value}={tokens[from_index].ttype}")
        logger.debug(f"Type={type(tokens[from_index])}")
        if isinstance(tokens[from_index], sqlparse.sql.Identifier):
            # Split on as keyword and collect the table name from 0th position. strip any spaces
            tables.append(tokens[from_index].value.split("as")[0].strip())
        from_index = from_index + 1

    return tables


def parse_custom_sql(
    ctx: PipelineContext,
    query: str,
    schema: Optional[str],
    database: Optional[str],
    platform: str,
    env: str,
    platform_instance: Optional[str],
) -> Optional["SqlParsingResult"]:

    logger.debug("Using sqlglot_lineage to parse custom sql")

    sql_query = remove_special_characters(query)
    logger.debug(f"Parsing sql={sql_query}")

    parsed_result: Optional["SqlParsingResult"] = None
    try:
        schema_resolver = (
            ctx.graph._make_schema_resolver(
                platform=platform,
                platform_instance=platform_instance,
                env=env,
            )
            if ctx.graph is not None
            else SchemaResolver(
                platform=platform,
                platform_instance=platform_instance,
                env=env,
                graph=None,
            )
        )

        parsed_result = sqlglot_lineage(
            sql_query,
            schema_resolver=schema_resolver,
            default_db=database,
            default_schema=schema,
        )
    except Exception as e:
        logger.debug(f"Fail to prase query {query}", exc_info=e)
        logger.warning("Fail to parse custom SQL")

    return parsed_result
