#!/bin/bash
set -euxo pipefail

# Runs a basic e2e test. It is not meant to be fully comprehensive,
# but rather should catch obvious bugs before they make it into prod.
#
# Script assumptions:
#   - The gradle build has already been run.
#   - Python 3.6+ is installed and in the PATH.

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip wheel setuptools
pip install -r requirements.txt

datahub docker quickstart \
	--build-locally \
	--quickstart-compose-file ../docker/docker-compose.yml \
	--quickstart-compose-file ../docker/docker-compose.override.yml \
	--quickstart-compose-file ../docker/docker-compose.dev.yml \
	--dump-logs-on-failure

(cd tests/cypress ; yarn install)

cd spark-smoke-test/docker

#build spark cluster images
./build_images.sh 

echo "--------------------------------------------------------------------"
echo "Bringing up spark cluster"
echo "--------------------------------------------------------------------"

#bring up spark cluster
docker-compose -f spark-docker-compose.yml up -d

echo "--------------------------------------------------------------------"
echo "Executing spark-submit jobs"
echo "--------------------------------------------------------------------"
#Execute spark-submit jobs
docker run --network datahub_network spark-submit

cd ../../

pytest -vv --continue-on-collection-errors --junit-xml=junit.smoke.xml
