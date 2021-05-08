from datahub.ingestion.run.pipeline import Pipeline
from tests.test_helpers import mce_helpers
import logging
from freezegun import freeze_time


logging.getLogger("lkml").setLevel(logging.INFO)

@freeze_time("2021-05-08 07:00:00")
def test_lookml_ingest(pytestconfig, tmp_path, mock_time):
    test_resources_dir = pytestconfig.rootpath / "tests/integration/lookml"

    print(str(test_resources_dir))

    pipeline = Pipeline.create(
        {
            "run_id": "lookml-test",
            "source": {
                "type": "lookml",
                "config": {
                    "base_folder": str(test_resources_dir),
                    "connection_to_platform_map": {"my_connection": "conn"},
                    "parse_table_names_from_sql": True,
                },
            },
            "sink": {
                "type": "file",
                "config": {
                    "filename": f"{tmp_path}/lookml_mces.json",
                },
            },
        }
    )
    pipeline.run()
    pipeline.raise_from_status()

    output = mce_helpers.load_json_file(str(tmp_path / "lookml_mces.json"))
    expected = mce_helpers.load_json_file(
        str(test_resources_dir / "expected_output.json")
    )
    mce_helpers.assert_mces_equal(output, expected)
