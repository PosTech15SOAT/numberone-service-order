#!/bin/sh
set -eu
exec java -javaagent:/app/dd-java-agent.jar -jar /app/app.jar "$@"
