#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SERVICE_NAME="${SW_AGENT_NAME:-myrent-backend}"
BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-192.168.100.128:11800}"
AGENT_VERSION="${SKYWALKING_AGENT_VERSION:-9.6.0}"
AGENT_DIR="${SKYWALKING_AGENT_DIR:-$PROJECT_ROOT/tools/skywalking-agent}"
APP_JAR_PATH="${APP_JAR_PATH:-}"
SKIP_BUILD="${SKIP_BUILD:-false}"
AGENT_JAR="$AGENT_DIR/skywalking-agent.jar"

if [[ ! -f "$AGENT_JAR" ]]; then
  "$SCRIPT_DIR/download-skywalking-agent.sh" "$AGENT_VERSION"
fi

OPTIONAL_SPRINGMVC_PLUGIN="$(find "$AGENT_DIR/optional-plugins" -maxdepth 1 -type f -name 'apm-springmvc-annotation-6.x-plugin-*.jar' | head -n 1)"
if [[ -n "$OPTIONAL_SPRINGMVC_PLUGIN" ]]; then
  ENABLED_PLUGIN="$AGENT_DIR/plugins/$(basename "$OPTIONAL_SPRINGMVC_PLUGIN")"
  if [[ ! -f "$ENABLED_PLUGIN" ]]; then
    cp "$OPTIONAL_SPRINGMVC_PLUGIN" "$ENABLED_PLUGIN"
    echo "Enabled optional plugin: $(basename "$OPTIONAL_SPRINGMVC_PLUGIN")"
  fi
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  (cd "$PROJECT_ROOT" && mvn -DskipTests package)
fi

if [[ -z "$APP_JAR_PATH" ]]; then
  APP_JAR_PATH="$(find "$PROJECT_ROOT/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)"
fi

if [[ -z "$APP_JAR_PATH" ]]; then
  echo "Cannot find application jar under target/. Run mvn package first or set APP_JAR_PATH." >&2
  exit 1
fi

AGENT_LOG_DIR="$PROJECT_ROOT/runtime-logs/skywalking-agent"
mkdir -p "$AGENT_LOG_DIR"

export SW_AGENT_NAME="$SERVICE_NAME"
export SW_AGENT_INSTANCE_NAME="${SW_AGENT_INSTANCE_NAME:-$(hostname)-$$}"
export SW_AGENT_COLLECTOR_BACKEND_SERVICES="$BACKEND_SERVICES"
export SW_LOGGING_DIR="$AGENT_LOG_DIR"

echo "Starting application with SkyWalking Agent ..."
echo "  service:  $SW_AGENT_NAME"
echo "  instance: $SW_AGENT_INSTANCE_NAME"
echo "  backend:  $SW_AGENT_COLLECTOR_BACKEND_SERVICES"
echo "  app jar:  $APP_JAR_PATH"
echo "  agent:    $AGENT_JAR"

cd "$PROJECT_ROOT"
exec java "-javaagent:$AGENT_JAR" -jar "$APP_JAR_PATH"
