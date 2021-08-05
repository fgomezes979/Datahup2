import subprocess

import pytest
from click.testing import CliRunner
from freezegun import freeze_time

from datahub.entrypoints import datahub
from tests.test_helpers import fs_helpers, mce_helpers
from tests.test_helpers.docker_helpers import wait_for_port

FROZEN_TIME = "2020-04-14 07:00:00"


@freeze_time(FROZEN_TIME)
@pytest.mark.integration
def test_kafka_ingest(docker_compose_runner, pytestconfig, tmp_path, mock_time):
    test_resources_dir = pytestconfig.rootpath / "tests/integration/kafka"

    with docker_compose_runner(
        test_resources_dir / "docker-compose.yml", "kafka"
    ) as docker_services:
        wait_for_port(docker_services, "test_broker", 9092, timeout=120)

        #TODO Set up topics and produce some data
        command = f"source {test_resources_dir}/send_records.sh"
        subprocess.run(command, shell=True, check=True)

        # Run the metadata ingestion pipeline.
        runner = CliRunner()
        with fs_helpers.isolated_filesystem(tmp_path):
            config_file = (test_resources_dir / "kafka_to_file.yml").resolve()
            result = runner.invoke(datahub, ["ingest", "-c", f"{config_file}"])
            assert result.exit_code == 0

        # Verify the output.
        #mce_helpers.check_golden_file(
        #    pytestconfig,
        #    output_path=tmp_path / "hive_mces.json",
        #    golden_path=test_resources_dir / "hive_mces_golden.json",
        #    ignore_paths=[
                # example: root[1]['proposedSnapshot']['com.linkedin.pegasus2avro.metadata.snapshot.DatasetSnapshot']['aspects'][0]['com.linkedin.pegasus2avro.dataset.DatasetProperties']['customProperties']['CreateTime:']
                # example: root[2]['proposedSnapshot']['com.linkedin.pegasus2avro.metadata.snapshot.DatasetSnapshot']['aspects'][0]['com.linkedin.pegasus2avro.dataset.DatasetProperties']['customProperties']['Table Parameters: transient_lastDdlTime']
        #        r"root\[\d+\]\['proposedSnapshot'\]\['com\.linkedin\.pegasus2avro\.metadata\.snapshot\.DatasetSnapshot'\]\['aspects'\]\[\d+\]\['com\.linkedin\.pegasus2avro\.dataset\.DatasetProperties'\]\['customProperties'\]\['.*Time.*'\]"
        #    ],
        #)
