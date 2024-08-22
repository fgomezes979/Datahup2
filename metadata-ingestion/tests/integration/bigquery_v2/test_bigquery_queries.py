import json
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

import pytest
from freezegun import freeze_time

from datahub.ingestion.source.usage.usage_common import BaseUsageConfig
from datahub.sql_parsing.sql_parsing_aggregator import ObservedQuery
from datahub.utilities.file_backed_collections import ConnectionWrapper, FileBackedList
from tests.test_helpers import mce_helpers
from tests.test_helpers.state_helpers import run_and_get_pipeline

FROZEN_TIME = "2024-08-19 07:00:00"


def _generate_queries_cached_file(tmp_path: Path, queries_json_path: Path) -> None:
    # We choose to generate Cached audit log (FileBackedList backed by sqlite) at runtime
    # instead of using pre-existing sqlite file here as default serializer for FileBackedList
    # uses pickle which may not work well across python versions.

    query_cache: FileBackedList[ObservedQuery] = FileBackedList(
        ConnectionWrapper(tmp_path / "audit_log.sqlite")
    )
    with open(queries_json_path, "r") as f:
        queries = json.load(f)
        assert isinstance(queries, list)
        for query in queries:
            query["timestamp"] = datetime.fromisoformat(query["timestamp"])
            query_cache.append(ObservedQuery(**query))

        query_cache.flush()


@freeze_time(FROZEN_TIME)
@patch("google.cloud.bigquery.Client")
def test_queries_ingestion(client, pytestconfig, monkeypatch, tmp_path):

    test_resources_dir = pytestconfig.rootpath / "tests/integration/bigquery_v2"
    mcp_golden_path = f"{test_resources_dir}/bigquery_queries_mcps_golden.json"
    mcp_output_path = "bigquery_queries_mcps.json"

    try:
        # query_log.json is originally created by using queries dump generated by
        # acryl bigquery connector smoke test and using `datahub check extract-sql-agg-log`
        # command with tablename="data" to convert cached audit log to queries json followed by
        # a simple `acryl-staging`->`gcp-staging` replacement.

        _generate_queries_cached_file(tmp_path, test_resources_dir / "query_log.json")
    except Exception as e:
        pytest.fail(f"Failed to generate queries sqlite cache: {e}")

    pipeline_config_dict: dict = {
        "source": {
            "type": "bigquery-queries",
            "config": {
                "project_ids": ["gcp-staging", "gcp-staging-2"],
                "local_temp_path": tmp_path,
            },
        },
        "sink": {"type": "file", "config": {"filename": mcp_output_path}},
    }

    # This is hacky to pick all queries instead of any 10.
    # Should be easy to remove once top_n_queries is supported in queries config
    monkeypatch.setattr(BaseUsageConfig.__fields__["top_n_queries"], "default", 20)

    pipeline = run_and_get_pipeline(pipeline_config_dict)
    pipeline.pretty_print_summary()

    mce_helpers.check_golden_file(
        pytestconfig,
        output_path=mcp_output_path,
        golden_path=mcp_golden_path,
    )
