#!/bin/bash

set -eux

GRADLE_DIR=$1
CERTS_DIR=$2

# used by the resolved_symlink below to resolve the path based on the JAVA_VERSION value. e.g. /usr/local/${JAVA_VERSION}
# shellcheck disable=SC2034
SYMLINK_PATTERN=$3
JAVA_SYMLINK_DIR=${4:-/usr/java}

symlink_dir="${SYMLINK_PATTERN%/*}"
mkdir -p "$symlink_dir"
mkdir -p "$JAVA_SYMLINK_DIR"

# Loading gradle jdk functions
SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPTS_DIR"/gradle-jdks-functions.sh

# Running the installation setup
install_and_setup_jdks "$GRADLE_DIR" "$CERTS_DIR" "$SCRIPTS_DIR"

for dir in "$GRADLE_DIR"/jdks/*/; do
  major_version_dir=${dir%*/}
  major_version=$(basename "$major_version_dir")
  if [ "$major_version" == "8" ]; then
    continue
  fi
  distribution_local_path=$(read_value "$major_version_dir"/"$OS"/"$ARCH"/local-path)
  jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
  resolved_symlink="${SYMLINK_PATTERN//\$\{JAVA_VERSION\}/$major_version}"
  # shellcheck disable=SC2154
  ln -s "$jdk_installation_directory" "$resolved_symlink"
  # Link java installations to /usr/java so that installations are automatically picked up by gradle
  ## https://github.com/gradle/gradle/blob/b381099260a04f226ef2412db8ee38fae3e9e753/subprojects/jvm-services/src/main/java/org/gradle/jvm/toolchain/internal/LinuxInstallationSupplier.java#L46
  ln -s "$jdk_installation_directory" "$JAVA_SYMLINK_DIR/$major_version"
done

cleanup
