#!/bin/sh

set -e

/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
if [ -f /palantir.crt ];then
  echo "Palantir cert:" $(/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradlejdks_palantir3rd-generationrootca -storepass changeit)
fi
echo "Amazon cert:" $(/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradlejdks_amazonrootca1 -storepass changeit)
