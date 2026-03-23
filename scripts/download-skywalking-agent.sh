#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-9.6.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INSTALL_DIR="${SKYWALKING_AGENT_DIR:-$PROJECT_ROOT/tools/skywalking-agent}"
AGENT_JAR="$INSTALL_DIR/skywalking-agent.jar"

if [[ -f "$AGENT_JAR" ]]; then
  echo "SkyWalking agent already exists: $AGENT_JAR"
  exit 0
fi

ARCHIVE_NAME="apache-skywalking-java-agent-$VERSION.tgz"
DOWNLOAD_URL="https://dlcdn.apache.org/skywalking/java-agent/$VERSION/$ARCHIVE_NAME"
TMP_DIR="$(mktemp -d)"
ARCHIVE_PATH="$TMP_DIR/$ARCHIVE_NAME"

mkdir -p "$INSTALL_DIR"

echo "Downloading SkyWalking Java Agent $VERSION ..."
curl -fL "$DOWNLOAD_URL" -o "$ARCHIVE_PATH"

echo "Extracting package to $INSTALL_DIR ..."
tar -xzf "$ARCHIVE_PATH" -C "$INSTALL_DIR" --strip-components=1

if [[ ! -f "$AGENT_JAR" ]]; then
  echo "SkyWalking package extraction failed." >&2
  exit 1
fi

rm -rf "$TMP_DIR"

echo "SkyWalking agent is ready: $AGENT_JAR"
