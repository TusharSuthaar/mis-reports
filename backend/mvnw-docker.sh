#!/usr/bin/env bash
# Runs Maven in a JDK 21 container (no local JDK needed). The Docker socket is mounted so the
# Testcontainers integration tests (*IT) can start Postgres.
#   ./mvnw-docker.sh verify        unit + integration tests
#   ./mvnw-docker.sh test          unit tests only
set -euo pipefail
cd "$(dirname "$0")"
src="$(pwd -W 2>/dev/null || pwd)"
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$src:/src" -w /src \
  -v epsilon-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9-eclipse-temurin-21 mvn -B "$@"
