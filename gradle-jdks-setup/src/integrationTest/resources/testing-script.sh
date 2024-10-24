#!/bin/sh

set -e

/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
# Running again the gradle-jdk-setup to check the JAVA_HOME.

# >>> Gradle JDK setup >>>
# !! Contents within this block are managed by 'palantir/gradle-jdks' !!
if [ -f gradle/gradle-jdks-setup.sh ]; then
    if ! . gradle/gradle-jdks-setup.sh; then
        echo "Failed to set up JDK, running gradle/gradle-jdks-setup.sh failed with non-zero exit code"
        exit 1
    fi
    # Setting JAVA_HOME to the gradle daemon to make sure gradlew uses this jdk for `JAVACMD`
    JAVA_HOME="$GRADLE_DAEMON_JDK"
fi
# <<< Gradle JDK setup <<<

echo "JAVA_HOME is set to: $JAVA_HOME"
