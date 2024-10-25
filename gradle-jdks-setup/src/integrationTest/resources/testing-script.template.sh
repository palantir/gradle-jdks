#!/bin/sh

set -e

/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
# Running again the gradle-jdk-setup to check the JAVA_HOME.

# inserting the lines from gradle-jdks/src/main/resources/gradlew-patch.sh
PLACEHOLDER_INSERT_GRADLEW_PATCH

echo "JAVA_HOME is set to: $JAVA_HOME"
