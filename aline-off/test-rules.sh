#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
javac -d "$DIR" app/src/main/java/com/alineoff/app/RuleEngine.java app/src/main/java/com/alineoff/app/NotificationPolicy.java app/src/main/java/com/alineoff/app/WhatsAppApps.java app/src/main/java/com/alineoff/app/ReceptionGate.java tests/RuleEngineTest.java
java -cp "$DIR" com.alineoff.app.RuleEngineTest
