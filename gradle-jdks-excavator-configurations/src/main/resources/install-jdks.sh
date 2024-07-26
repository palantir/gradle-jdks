#!/bin/bash

set -eux

DIR="$(dirname "$(readlink -f "$0")")"

source "$DIR"/gradle-jdks-functions.sh

GRADLE_JDKS_DIR=$1
CERTS_DIR=$2

GRADLE_JDKS_HOME=$(get_gradle_jdks_home)
mkdir -p "$GRADLE_JDKS_HOME"

os_name=$(get_os)
arch_name=$(get_arch)

for dir in "$GRADLE_JDKS_DIR"/*/; do
  major_version_dir=${dir%*/}
  distribution_local_path=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/local-path)
  distribution_url=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/download-url)
  jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
  echo "JDK installation '$jdk_installation_directory' does not exist, installing '$distribution_url' in progress ..."
  case "$distribution_url" in
    *.zip)
      curl -C - "$distribution_url" -o "$jdk_installation_directory".zip
      tar -xzf "$jdk_installation_directory".zip
      ;;
    *)
      mkdir -p "$jdk_installation_directory"
      curl -C - "$distribution_url" | tar -xzf - --strip-components=1 -C "$jdk_installation_directory"
      ;;
  esac
  java_home=$(get_java_home "$jdk_installation_directory")
  # Add Java truststore
  for cert_path in "$CERTS_DIR"/Palantir*; do
    # if the glob is not matched, skip
    [ -e "$cert_path" ] || continue
    cert_name=$(basename "$cert_path")
    echo "Adding ${cert_name} to ${java_home} truststore"
    "$java_home"/bin/keytool -import -trustcacerts -file "$cert_path" -alias "$cert_name" -keystore "$java_home"/lib/security/cacerts -storepass changeit -noprompt
  done
done
