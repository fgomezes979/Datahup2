import json
from datahub.cli import cli_utils
from datahub.ingestion.run.pipeline import Pipeline

GMS_ENDPOINT = "http://localhost:8080"
FRONTEND_ENDPOINT = "http://localhost:9002"

def ingest_file_via_rest(filename: str):
    pipeline = Pipeline.create(
        {
            "source": {
                "type": "file",
                "config": {"filename": filename},
            },
            "sink": {
                "type": "datahub-rest",
                "config": {"server": GMS_ENDPOINT},
            },
        }
    )
    pipeline.run()
    pipeline.raise_from_status()


def delete_urns_from_file(filename: str):
    with open(filename) as f:
        d = json.load(f)
        for entry in d:
            is_mcp = 'entityUrn' in entry
            urn = None
            # Kill Snapshot
            if is_mcp:
              urn = entry['entityUrn']
            else:
              snapshot_union = entry['proposedSnapshot']
              snapshot = list(snapshot_union.values())[0]
              urn = snapshot['urn']
            print("About to delete urn")
            print(urn)
            payload_obj = {"urn": urn}
            cli_utils.post_delete_endpoint(
                payload_obj, "/entities?action=delete"
            )
