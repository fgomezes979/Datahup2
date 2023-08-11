import json
import logging
import os
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from typing import Any, Dict, Optional, cast
from unittest.mock import MagicMock, Mock, patch

import pytest
from google.api_core.exceptions import GoogleAPICallError
from google.cloud.bigquery.table import Row, TableListItem

from datahub.ingestion.api.common import PipelineContext
from datahub.ingestion.source.bigquery_v2.bigquery import BigqueryV2Source
from datahub.ingestion.source.bigquery_v2.bigquery_audit import (
    _BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX,
    BigqueryTableIdentifier,
    BigQueryTableRef,
)
from datahub.ingestion.source.bigquery_v2.bigquery_config import BigQueryV2Config
from datahub.ingestion.source.bigquery_v2.bigquery_report import BigQueryV2Report
from datahub.ingestion.source.bigquery_v2.bigquery_schema_api import (
    BigqueryProject,
    BigQueryTechnicalSchemaApi,
    BigqueryView,
)
from datahub.ingestion.source.bigquery_v2.lineage import (
    LineageEdge,
    LineageEdgeColumnMapping,
)
from datahub.metadata.com.linkedin.pegasus2avro.dataset import ViewProperties
from datahub.metadata.schema_classes import MetadataChangeProposalClass


def test_bigquery_uri():
    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )
    assert config.get_sql_alchemy_url() == "bigquery://"


def test_bigquery_uri_on_behalf():
    config = BigQueryV2Config.parse_obj(
        {"project_id": "test-project", "project_on_behalf": "test-project-on-behalf"}
    )
    assert config.get_sql_alchemy_url() == "bigquery://test-project-on-behalf"


def test_bigquery_uri_with_credential():
    expected_credential_json = {
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "client_email": "test@acryl.io",
        "client_id": "test_client-id",
        "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test@acryl.io",
        "private_key": "random_private_key",
        "private_key_id": "test-private-key",
        "project_id": "test-project",
        "token_uri": "https://oauth2.googleapis.com/token",
        "type": "service_account",
    }

    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
            "credential": {
                "project_id": "test-project",
                "private_key_id": "test-private-key",
                "private_key": "random_private_key",
                "client_email": "test@acryl.io",
                "client_id": "test_client-id",
            },
        }
    )

    try:
        assert config.get_sql_alchemy_url() == "bigquery://"
        assert config._credentials_path

        with open(config._credentials_path) as jsonFile:
            json_credential = json.load(jsonFile)
            jsonFile.close()

        credential = json.dumps(json_credential, sort_keys=True)
        expected_credential = json.dumps(expected_credential_json, sort_keys=True)
        assert expected_credential == credential

    except AssertionError as e:
        if config._credentials_path:
            os.unlink(str(config._credentials_path))
        raise e


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_with_project_ids(get_bigquery_client):
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    config = BigQueryV2Config.parse_obj(
        {
            "project_ids": ["test-1", "test-2"],
        }
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test1"))
    assert source._get_projects() == [
        BigqueryProject("test-1", "test-1"),
        BigqueryProject("test-2", "test-2"),
    ]
    assert client_mock.list_projects.call_count == 0

    config = BigQueryV2Config.parse_obj(
        {"project_ids": ["test-1", "test-2"], "project_id": "test-3"}
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test2"))
    assert source._get_projects() == [
        BigqueryProject("test-1", "test-1"),
        BigqueryProject("test-2", "test-2"),
    ]
    assert client_mock.list_projects.call_count == 0


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_with_project_ids_overrides_project_id_pattern(
    get_bigquery_client,
):
    config = BigQueryV2Config.parse_obj(
        {
            "project_ids": ["test-project", "test-project-2"],
            "project_id_pattern": {"deny": ["^test-project$"]},
        }
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    projects = source._get_projects()
    assert projects == [
        BigqueryProject(id="test-project", name="test-project"),
        BigqueryProject(id="test-project-2", name="test-project-2"),
    ]


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_dataplatform_instance_aspect_returns_project_id(get_bigquery_client):
    project_id = "project_id"
    expected_instance = (
        f"urn:li:dataPlatformInstance:(urn:li:dataPlatform:bigquery,{project_id})"
    )

    config = BigQueryV2Config.parse_obj({})
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))

    data_platform_instance = source.get_dataplatform_instance_aspect(
        "urn:li:test", project_id
    )

    metadata = data_platform_instance.get_metadata()["metadata"]

    assert data_platform_instance is not None
    assert metadata.aspectName == "dataPlatformInstance"
    assert metadata.aspect.instance == expected_instance


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_with_single_project_id(get_bigquery_client):
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    config = BigQueryV2Config.parse_obj({"project_id": "test-3"})
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test1"))
    assert source._get_projects() == [
        BigqueryProject("test-3", "test-3"),
    ]
    assert client_mock.list_projects.call_count == 0


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_by_list(get_bigquery_client):
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    client_mock.list_projects.return_value = [
        SimpleNamespace(
            project_id="test-1",
            friendly_name="one",
        ),
        SimpleNamespace(
            project_id="test-2",
            friendly_name="two",
        ),
    ]

    config = BigQueryV2Config.parse_obj({})
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test1"))
    assert source._get_projects() == [
        BigqueryProject("test-1", "one"),
        BigqueryProject("test-2", "two"),
    ]
    assert client_mock.list_projects.call_count == 1


@patch.object(BigQueryTechnicalSchemaApi, "get_projects")
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_filter_by_pattern(get_bigquery_client, get_projects_mock):
    get_projects_mock.return_value = [
        BigqueryProject("test-project", "Test Project"),
        BigqueryProject("test-project-2", "Test Project 2"),
    ]

    config = BigQueryV2Config.parse_obj(
        {"project_id_pattern": {"deny": ["^test-project$"]}}
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    projects = source._get_projects()
    assert projects == [
        BigqueryProject(id="test-project-2", name="Test Project 2"),
    ]


@patch.object(BigQueryTechnicalSchemaApi, "get_projects")
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_list_empty(get_bigquery_client, get_projects_mock):
    get_projects_mock.return_value = []

    config = BigQueryV2Config.parse_obj(
        {"project_id_pattern": {"deny": ["^test-project$"]}}
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    projects = source._get_projects()
    assert len(source.report.failures) == 1
    assert projects == []


@patch.object(BigQueryTechnicalSchemaApi, "get_projects")
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_list_failure(
    get_bigquery_client: MagicMock,
    get_projects_mock: MagicMock,
    caplog: pytest.LogCaptureFixture,
) -> None:
    error_str = "my error"
    get_projects_mock.side_effect = GoogleAPICallError(error_str)

    config = BigQueryV2Config.parse_obj(
        {"project_id_pattern": {"deny": ["^test-project$"]}}
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    caplog.records.clear()
    with caplog.at_level(logging.ERROR):
        projects = source._get_projects()
        assert len(caplog.records) == 1
        assert error_str in caplog.records[0].msg
    assert len(source.report.failures) == 1
    assert projects == []


@patch.object(BigQueryTechnicalSchemaApi, "get_projects")
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_projects_list_fully_filtered(get_projects_mock, get_bigquery_client):
    get_projects_mock.return_value = [BigqueryProject("test-project", "Test Project")]

    config = BigQueryV2Config.parse_obj(
        {"project_id_pattern": {"deny": ["^test-project$"]}}
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    projects = source._get_projects()
    assert len(source.report.failures) == 0
    assert projects == []


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_simple_upstream_table_generation(get_bigquery_client):
    a: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="a"
        )
    )
    b: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="b"
        )
    )

    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    lineage_metadata = {
        str(a): {
            LineageEdge(
                table=str(b), auditStamp=datetime.now(), column_mapping=frozenset()
            )
        }
    }
    upstreams = source.lineage_extractor.get_upstream_tables(a, lineage_metadata)

    assert len(upstreams) == 1
    assert list(upstreams)[0].table == str(b)


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_upstream_table_generation_with_temporary_table_without_temp_upstream(
    get_bigquery_client,
):
    a: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="a"
        )
    )
    b: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="_temp-dataset", table="b"
        )
    )

    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))

    lineage_metadata = {
        str(a): {
            LineageEdge(
                table=str(b), auditStamp=datetime.now(), column_mapping=frozenset()
            )
        }
    }
    upstreams = source.lineage_extractor.get_upstream_tables(a, lineage_metadata)
    assert list(upstreams) == []


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_upstream_table_column_lineage_with_temp_table(get_bigquery_client):
    from datahub.ingestion.api.common import PipelineContext

    a: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="a"
        )
    )
    b: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="_temp-dataset", table="b"
        )
    )
    c: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="c"
        )
    )

    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )

    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    lineage_metadata = {
        str(a): {
            LineageEdge(
                table=str(b),
                auditStamp=datetime.now(),
                column_mapping=frozenset(
                    [
                        LineageEdgeColumnMapping(
                            "a_col1", in_columns=frozenset(["b_col2", "b_col3"])
                        )
                    ]
                ),
                column_confidence=0.8,
            )
        },
        str(b): {
            LineageEdge(
                table=str(c),
                auditStamp=datetime.now(),
                column_mapping=frozenset(
                    [
                        LineageEdgeColumnMapping(
                            "b_col2", in_columns=frozenset(["c_col1", "c_col2"])
                        ),
                        LineageEdgeColumnMapping(
                            "b_col3", in_columns=frozenset(["c_col2", "c_col3"])
                        ),
                    ]
                ),
                column_confidence=0.7,
            )
        },
    }
    upstreams = source.lineage_extractor.get_upstream_tables(a, lineage_metadata)
    assert len(upstreams) == 1

    upstream = list(upstreams)[0]
    assert upstream.table == str(c)
    assert upstream.column_mapping == frozenset(
        [
            LineageEdgeColumnMapping(
                "a_col1", in_columns=frozenset(["c_col1", "c_col2", "c_col3"])
            )
        ]
    )
    assert upstream.column_confidence == 0.7


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_upstream_table_generation_with_temporary_table_with_multiple_temp_upstream(
    get_bigquery_client,
):
    a: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="a"
        )
    )
    b: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="_temp-dataset", table="b"
        )
    )
    c: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="c"
        )
    )
    d: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="_test-dataset", table="d"
        )
    )
    e: BigQueryTableRef = BigQueryTableRef(
        BigqueryTableIdentifier(
            project_id="test-project", dataset="test-dataset", table="e"
        )
    )

    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )
    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))
    lineage_metadata = {
        str(a): {
            LineageEdge(
                table=str(b), auditStamp=datetime.now(), column_mapping=frozenset()
            )
        },
        str(b): {
            LineageEdge(
                table=str(c), auditStamp=datetime.now(), column_mapping=frozenset()
            ),
            LineageEdge(
                table=str(d), auditStamp=datetime.now(), column_mapping=frozenset()
            ),
        },
        str(d): {
            LineageEdge(
                table=str(e), auditStamp=datetime.now(), column_mapping=frozenset()
            )
        },
    }
    upstreams = source.lineage_extractor.get_upstream_tables(a, lineage_metadata)
    sorted_list = list(upstreams)
    sorted_list.sort()
    assert sorted_list[0].table == str(c)
    assert sorted_list[1].table == str(e)


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_schema_api.BigQueryTechnicalSchemaApi.get_tables_for_dataset"
)
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_table_processing_logic(get_bigquery_client, data_dictionary_mock):
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )

    tableListItems = [
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "test-table",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "test-sharded-table_20220102",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "test-sharded-table_20210101",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "test-sharded-table_20220101",
                }
            }
        ),
    ]

    client_mock.list_tables.return_value = tableListItems
    data_dictionary_mock.get_tables_for_dataset.return_value = None

    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))

    _ = list(
        source.get_tables_for_dataset(
            project_id="test-project", dataset_name="test-dataset"
        )
    )

    assert data_dictionary_mock.call_count == 1

    # args only available from python 3.8 and that's why call_args_list is sooo ugly
    tables: Dict[str, TableListItem] = data_dictionary_mock.call_args_list[0][0][
        2
    ]  # alternatively
    for table in tables.keys():
        assert table in ["test-table", "test-sharded-table_20220102"]


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_schema_api.BigQueryTechnicalSchemaApi.get_tables_for_dataset"
)
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_table_processing_logic_date_named_tables(
    get_bigquery_client, data_dictionary_mock
):
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    # test that tables with date names are processed correctly
    config = BigQueryV2Config.parse_obj(
        {
            "project_id": "test-project",
        }
    )

    tableListItems = [
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "test-table",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "20220102",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "20210101",
                }
            }
        ),
        TableListItem(
            {
                "tableReference": {
                    "projectId": "test-project",
                    "datasetId": "test-dataset",
                    "tableId": "20220103",
                }
            }
        ),
    ]

    client_mock.list_tables.return_value = tableListItems
    data_dictionary_mock.get_tables_for_dataset.return_value = None

    source = BigqueryV2Source(config=config, ctx=PipelineContext(run_id="test"))

    _ = list(
        source.get_tables_for_dataset(
            project_id="test-project", dataset_name="test-dataset"
        )
    )

    assert data_dictionary_mock.call_count == 1

    # args only available from python 3.8 and that's why call_args_list is sooo ugly
    tables: Dict[str, TableListItem] = data_dictionary_mock.call_args_list[0][0][
        2
    ]  # alternatively
    for table in tables.keys():
        assert tables[table].table_id in ["test-table", "20220103"]


def create_row(d: Dict[str, Any]) -> Row:
    values = []
    field_to_index = {}
    for i, (k, v) in enumerate(d.items()):
        field_to_index[k] = i
        values.append(v)
    return Row(tuple(values), field_to_index)


@pytest.fixture
def bigquery_view_1() -> BigqueryView:
    now = datetime.now(tz=timezone.utc)
    return BigqueryView(
        name="table1",
        created=now - timedelta(days=10),
        last_altered=now - timedelta(hours=1),
        comment="comment1",
        view_definition="CREATE VIEW 1",
        materialized=False,
    )


@pytest.fixture
def bigquery_view_2() -> BigqueryView:
    now = datetime.now(tz=timezone.utc)
    return BigqueryView(
        name="table2",
        created=now,
        last_altered=now,
        comment="comment2",
        view_definition="CREATE VIEW 2",
        materialized=True,
    )


@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_schema_api.BigQueryTechnicalSchemaApi.get_query_result"
)
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_get_views_for_dataset(
    get_bigquery_client: Mock,
    query_mock: Mock,
    bigquery_view_1: BigqueryView,
    bigquery_view_2: BigqueryView,
) -> None:
    client_mock = MagicMock()
    get_bigquery_client.return_value = client_mock
    assert bigquery_view_1.last_altered
    row1 = create_row(
        dict(
            table_name=bigquery_view_1.name,
            created=bigquery_view_1.created,
            last_altered=bigquery_view_1.last_altered.timestamp() * 1000,
            comment=bigquery_view_1.comment,
            view_definition=bigquery_view_1.view_definition,
            table_type="VIEW",
        )
    )
    row2 = create_row(  # Materialized view, no last_altered
        dict(
            table_name=bigquery_view_2.name,
            created=bigquery_view_2.created,
            comment=bigquery_view_2.comment,
            view_definition=bigquery_view_2.view_definition,
            table_type="MATERIALIZED VIEW",
        )
    )
    query_mock.return_value = [row1, row2]
    bigquery_data_dictionary = BigQueryTechnicalSchemaApi(
        BigQueryV2Report(), client_mock
    )

    views = bigquery_data_dictionary.get_views_for_dataset(
        project_id="test-project",
        dataset_name="test-dataset",
        has_data_read=False,
    )
    assert list(views) == [bigquery_view_1, bigquery_view_2]


@patch.object(BigqueryV2Source, "gen_dataset_workunits", lambda *args, **kwargs: [])
@patch(
    "datahub.ingestion.source.bigquery_v2.bigquery_config.BigQueryV2Config.get_bigquery_client"
)
def test_gen_view_dataset_workunits(
    get_bigquery_client, bigquery_view_1, bigquery_view_2
):
    project_id = "test-project"
    dataset_name = "test-dataset"
    config = BigQueryV2Config.parse_obj(
        {
            "project_id": project_id,
        }
    )
    source: BigqueryV2Source = BigqueryV2Source(
        config=config, ctx=PipelineContext(run_id="test")
    )

    gen = source.gen_view_dataset_workunits(
        bigquery_view_1, [], project_id, dataset_name
    )
    mcp = cast(MetadataChangeProposalClass, next(iter(gen)).metadata)
    assert mcp.aspect == ViewProperties(
        materialized=bigquery_view_1.materialized,
        viewLanguage="SQL",
        viewLogic=bigquery_view_1.view_definition,
    )

    gen = source.gen_view_dataset_workunits(
        bigquery_view_2, [], project_id, dataset_name
    )
    mcp = cast(MetadataChangeProposalClass, next(iter(gen)).metadata)
    assert mcp.aspect == ViewProperties(
        materialized=bigquery_view_2.materialized,
        viewLanguage="SQL",
        viewLogic=bigquery_view_2.view_definition,
    )


@pytest.mark.parametrize(
    "table_name, expected_table_prefix, expected_shard",
    [
        # Cases with Fully qualified name as input
        ("project.dataset.table", "project.dataset.table", None),
        ("project.dataset.table_20231215", "project.dataset.table", "20231215"),
        ("project.dataset.table_2023", "project.dataset.table_2023", None),
        # incorrectly handled special case where dataset itself is a sharded table if full name is specified
        ("project.dataset.20231215", "project.dataset.20231215", None),
        # Cases with Just the table name as input
        ("table", "table", None),
        ("table20231215", "table20231215", None),
        ("table_20231215", "table", "20231215"),
        ("table_1624046611000_name", "table_1624046611000_name", None),
        ("table_1624046611000", "table_1624046611000", None),
        # Special case where dataset itself is a sharded table
        ("20231215", None, "20231215"),
    ],
)
def test_get_table_and_shard_default(
    table_name: str, expected_table_prefix: Optional[str], expected_shard: Optional[str]
) -> None:
    with patch(
        "datahub.ingestion.source.bigquery_v2.bigquery_audit.BigqueryTableIdentifier._BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX",
        _BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX,
    ):
        assert BigqueryTableIdentifier.get_table_and_shard(table_name) == (
            expected_table_prefix,
            expected_shard,
        )


@pytest.mark.parametrize(
    "table_name, expected_table_prefix, expected_shard",
    [
        # Cases with Fully qualified name as input
        ("project.dataset.table", "project.dataset.table", None),
        ("project.dataset.table_20231215", "project.dataset.table", "20231215"),
        ("project.dataset.table_2023", "project.dataset.table", "2023"),
        # incorrectly handled special case where dataset itself is a sharded table if full name is specified
        ("project.dataset.20231215", "project.dataset.20231215", None),
        ("project.dataset.2023", "project.dataset.2023", None),
        # Cases with Just the table name as input
        ("table", "table", None),
        ("table20231215", "table20231215", None),
        ("table_20231215", "table", "20231215"),
        ("table_2023", "table", "2023"),
        ("table_1624046611000_name", "table_1624046611000_name", None),
        ("table_1624046611000", "table_1624046611000", None),
        ("table_1624046611", "table", "1624046611"),
        # Special case where dataset itself is a sharded table
        ("20231215", None, "20231215"),
        ("2023", None, "2023"),
    ],
)
def test_get_table_and_shard_custom_shard_pattern(
    table_name: str, expected_table_prefix: Optional[str], expected_shard: Optional[str]
) -> None:
    with patch(
        "datahub.ingestion.source.bigquery_v2.bigquery_audit.BigqueryTableIdentifier._BIGQUERY_DEFAULT_SHARDED_TABLE_REGEX",
        "((.+)[_$])?(\\d{4,10})$",
    ):
        assert BigqueryTableIdentifier.get_table_and_shard(table_name) == (
            expected_table_prefix,
            expected_shard,
        )


@pytest.mark.parametrize(
    "full_table_name, datahub_full_table_name",
    [
        ("project.dataset.table", "project.dataset.table"),
        ("project.dataset.table_20231215", "project.dataset.table"),
        ("project.dataset.table@1624046611000", "project.dataset.table"),
        ("project.dataset.table@-9600", "project.dataset.table"),
        ("project.dataset.table@-3600000", "project.dataset.table"),
        ("project.dataset.table@-3600000--1800000", "project.dataset.table"),
        ("project.dataset.table@1624046611000-1612046611000", "project.dataset.table"),
        ("project.dataset.table@-3600000-", "project.dataset.table"),
        ("project.dataset.table@1624046611000-", "project.dataset.table"),
        (
            "project.dataset.table_1624046611000_name",
            "project.dataset.table_1624046611000_name",
        ),
        ("project.dataset.table_1624046611000", "project.dataset.table_1624046611000"),
        ("project.dataset.table20231215", "project.dataset.table20231215"),
        ("project.dataset.table_*", "project.dataset.table"),
        ("project.dataset.table_2023*", "project.dataset.table"),
        ("project.dataset.table_202301*", "project.dataset.table"),
        # Special case where dataset itself is a sharded table
        ("project.dataset.20230112", "project.dataset.dataset"),
    ],
)
def test_get_table_name(full_table_name: str, datahub_full_table_name: str) -> None:
    with patch(
        "datahub.ingestion.source.bigquery_v2.bigquery_audit.BigqueryTableIdentifier._BQ_SHARDED_TABLE_SUFFIX",
        "",
    ):
        assert (
            BigqueryTableIdentifier.from_string_name(full_table_name).get_table_name()
            == datahub_full_table_name
        )
