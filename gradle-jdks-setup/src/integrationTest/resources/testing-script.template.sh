#!/bin/sh

set -e

/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
# Running again the gradle-jdk-setup to check the JAVA_HOME.

if [ -s /logsOutput/stdout ]; then
  echo "Unexpected output from build time: $(cat /logsOutput/stdout)"
fi

# inserting the lines from gradle-jdks/src/main/resources/gradlew-patch.sh
PLACEHOLDER_INSERT_GRADLEW_PATCH

echo "JAVA_HOME is set to: $JAVA_HOME"

# running again Gradle JDKs setup, won't write anything to stdout/stderr
. /gradle/gradle-jdks-setup.sh > /tmp/all-output 2>&1
if [ -s /tmp/all-output ]; then
  echo "Unexpected output after all JDKs were installed: $(cat /tmp/all-output)"
fi

if [ ! -f /root/.gradle/gradle-jdks/graalvm-community-jdk-23.0.1/bin/java ]; then
  echo "GraalVM is not set"
else
  /root/.gradle/gradle-jdks/graalvm-community-jdk-23.0.1/bin/java -version
fi
