#!/bin/sh
# Maven Wrapper script
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file for details.

set -e

# OS specific support.
cygwin=false
darwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MINGW*)  mingw=true  ;;
esac

# Determine the project base directory.
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="mvnw"

# Find Java.
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "Error: JAVA_HOME is not set and java is not on PATH." >&2
  exit 1
fi

MAVEN_WRAPPER_JAR="$APP_HOME/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$APP_HOME/.mvn/wrapper/maven-wrapper.properties"

# Download the wrapper jar if missing.
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep "^wrapperUrl=" "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2-)
  echo "Downloading Maven Wrapper from $WRAPPER_URL"
  if command -v wget >/dev/null 2>&1; then
    wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" || {
      echo "Could not download Maven Wrapper JAR" >&2; exit 1
    }
  elif command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" || {
      echo "Could not download Maven Wrapper JAR" >&2; exit 1
    }
  else
    echo "Neither wget nor curl is available. Cannot download Maven Wrapper." >&2
    exit 1
  fi
fi

exec "$JAVACMD" \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$APP_HOME" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
