#!/bin/bash

set -eux

GRADLE_DIR=$1
CERTS_DIR=$2

# used by the resolved_symlink below to resolve the path based on the JAVA_VERSION value. e.g. /usr/local/${JAVA_VERSION}
# shellcheck disable=SC2034
SYMLINK_PATTERN=$3

# Loading gradle jdk functions
SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPTS_DIR"/gradle-jdks-functions.sh

# Running the installation setup
install_and_setup_jdks "$GRADLE_DIR" "$CERTS_DIR" "$SCRIPTS_DIR"

os_name=$(get_os)
arch_name=$(get_arch)

# adding symlinks to java 11, 17 & 21
for dir in "$GRADLE_DIR"/jdks/*/; do
  major_version_dir=${dir%*/}
  major_version=$(basename "$major_version_dir")
  if [ "$major_version" == "8" ]; then
    continue
  fi
  distribution_local_path=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/local-path)
  jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
  JAVA_VERSION=$major_version  && eval "resolved_symlink=$SYMLINK_PATTERN"
  # shellcheck disable=SC2154
  ln -s "$jdk_installation_directory" "$resolved_symlink"
done

cleanup
