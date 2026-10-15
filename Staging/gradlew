#!/bin/sh
DIR=""
case "$0" in
    /*) DIR="${0%/*}" ;;
    *) DIR="${PWD}/${0%/*}" ;;
esac
if [ -d "$DIR/gradle/wrapper" ]; then
    WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
fi
if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$DIR/gradle/wrapper"
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -o "$DIR/gradle/wrapper/gradle-wrapper.jar"
fi
exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
