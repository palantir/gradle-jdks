#!/bin/sh

set -e

if [ -f ignore-certs-curl-wget.sh ]; then
  . ignore-certs-curl-wget.sh
fi

source gradle/gradle-jdks-setup.sh

echo "Java home is: $JAVA_HOME"
echo "Java path is: $(type java)"
echo "Java version is: $(java --version | awk '{print $2}' | head -n 1)"
