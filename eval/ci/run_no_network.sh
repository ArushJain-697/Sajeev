#!/bin/sh
# #65: run the engine tests in a container with NO network, as the ticket requires. Needs Docker running and a Maven
# local repository that already holds the dependencies (the container cannot download anything).
# Usage: eval/ci/run_no_network.sh <maven-local-repo-dir> <models-dir>
set -eu
REPO="$1"; MODELS="$2"; ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
exec docker run --rm --network none -v "$ROOT":/w -v "$REPO":/m2 -v "$MODELS":/models -w /w \
  maven:3.9-eclipse-temurin-17 mvn -o -q -Dmaven.repo.local=/m2 -pl liveness-api,liveness-core test -Dsajeev.models=/models
