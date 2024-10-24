#!/bin/sh

set -e

if ! "$GRADLE_DAEMON_JDK/bin/java" -version 2>&1 | grep -q "11.0.21.9.1"; then
  echo "Invalid JAVA_HOME: $GRADLE_DAEMON_JDK" >&2
fi
/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
if [ -f /palantir.crt ];then
  echo "Palantir cert:" $(/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradlejdks_palantir3rd-generationrootca -storepass changeit)
fi
echo "Example.com cert:" $(/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradleJdks_example.com -storepass changeit)
