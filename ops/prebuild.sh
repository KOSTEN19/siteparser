#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found. Install Maven first."
  exit 1
fi

echo "Building common + scheduler + crawler jars..."
mvn -B -pl common,scheduler-service,crawler-service -am -DskipTests package

SCHED_JAR="${ROOT_DIR}/scheduler-service/target/scheduler-service-0.1.0.jar"
CRAWLER_JAR="${ROOT_DIR}/crawler-service/target/crawler-service-0.1.0.jar"

if [[ ! -f "${SCHED_JAR}" ]]; then
  echo "Missing ${SCHED_JAR}"
  exit 1
fi
if [[ ! -f "${CRAWLER_JAR}" ]]; then
  echo "Missing ${CRAWLER_JAR}"
  exit 1
fi

mkdir -p "${ROOT_DIR}/output"
echo "Prebuild complete."
echo "Ready jars:"
echo " - ${SCHED_JAR}"
echo " - ${CRAWLER_JAR}"
