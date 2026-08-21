#!/bin/sh

# day01과 같은 위치에서 공용 Gradle Wrapper 바이너리를 사용한다.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$APP_HOME/../day01/gradlew" -p "$APP_HOME" "$@"

