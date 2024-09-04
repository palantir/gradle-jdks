#!/bin/sh

set -e

. gradle/gradle-jdks-setup.sh

/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/java -version
if [ -f /palantir.crt ];then
  /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradlejdks_palantir3rd-generationrootca -storepass changeit
fi
/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/bin/keytool -keystore /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1/lib/security/cacerts -list -alias gradlejdks_amazonrootca1 -storepass changeit
