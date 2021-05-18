import pytest
from feast import Client
from feast.data_format import ParquetFormat
from feast.data_source import FileSource
from feast.entity import Entity
from feast.feature import Feature
from feast.feature_table import FeatureTable
from feast.value_type import ValueType

from datahub.ingestion.run.pipeline import Pipeline
from tests.test_helpers import mce_helpers
from tests.test_helpers.docker_helpers import wait_for_port


# make sure that mock_time is excluded here because it messes with feast
@pytest.mark.slow
def test_feast_ingest(docker_compose_runner, pytestconfig, tmp_path):
    test_resources_dir = pytestconfig.rootpath / "tests/integration/feast"

    with docker_compose_runner(
        test_resources_dir / "docker-compose.yml", "feast"
    ) as docker_services:
        wait_for_port(docker_services, "testfeast", 6565)

        test_client = Client(core_url="localhost:6565")

        # create dummy entity since Feast demands it
        entity = Entity(
            name="dummy_entity",
            description="Dummy entity",
            value_type=ValueType.STRING,
            labels={"key": "val"},
        )

        print("Breakpoint")

        test_client.apply(entity)

        # create feature tables
        batch_source = FileSource(
            file_format=ParquetFormat(),
            file_url="file://feast/*",
            event_timestamp_column="ts_col",
            created_timestamp_column="timestamp",
            date_partition_column="date_partition_col",
        )

        table = FeatureTable(
            name="test_feature_table",
            features=[
                Feature(name="test_BYTES_feature", dtype=ValueType.BYTES),
                Feature(name="test_STRING_feature", dtype=ValueType.STRING),
                Feature(name="test_INT32_feature", dtype=ValueType.INT32),
                Feature(name="test_INT64_feature", dtype=ValueType.INT64),
                Feature(name="test_DOUBLE_feature", dtype=ValueType.DOUBLE),
                Feature(name="test_FLOAT_feature", dtype=ValueType.FLOAT),
                Feature(name="test_BOOL_feature", dtype=ValueType.BOOL),
                Feature(name="test_BYTES_LIST_feature", dtype=ValueType.BYTES_LIST),
                Feature(name="test_STRING_LIST_feature", dtype=ValueType.STRING_LIST),
                Feature(name="test_INT32_LIST_feature", dtype=ValueType.INT32_LIST),
                Feature(name="test_INT64_LIST_feature", dtype=ValueType.INT64_LIST),
                Feature(name="test_DOUBLE_LIST_feature", dtype=ValueType.DOUBLE_LIST),
                Feature(name="test_FLOAT_LIST_feature", dtype=ValueType.FLOAT_LIST),
                Feature(name="test_BOOL_LIST_feature", dtype=ValueType.BOOL_LIST),
            ],
            entities=["dummy_entity"],
            labels={"team": "matchmaking"},
            batch_source=batch_source,
        )

        test_client.apply(table)

        for table in test_client.list_feature_tables():

            print(test_client.get_feature_table(table.name))

    # # Run the metadata ingestion pipeline.
    # pipeline = Pipeline.create(
    #     {
    #         "run_id": "feast-test",
    #         "source": {
    #             "type": "feast",
    #             "config": {
    #                 "core_url": "feast://localhost:6565",
    #             },
    #         },
    #         "sink": {
    #             "type": "file",
    #             "config": {
    #                 "filename": f"{tmp_path}/feast_mces.json",
    #             },
    #         },
    #     }
    # )
    # pipeline.run()
    # pipeline.raise_from_status()

    # # Verify the output.
    # output = mce_helpers.load_json_file(str(tmp_path / "feast_mces.json"))
    # golden = mce_helpers.load_json_file(
    #     str(test_resources_dir / "feast_mce_golden.json")
    # )
    # mce_helpers.assert_mces_equal(output, golden)
