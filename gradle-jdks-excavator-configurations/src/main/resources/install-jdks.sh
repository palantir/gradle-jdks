#!/bin/bash

set -eux

GRADLE_DIR=$1
CERTS_DIR=$2

# Loading gradle jdk functions
DIR="$(dirname "$(readlink -f "$0")")"
source "$DIR"/gradle-jdks-functions.sh

# Running the installation setup
install_and_setup_jdks "$GRADLE_DIR" "$CERTS_DIR"

os_name=$(get_os)
arch_name=$(get_arch)

# adding symlinks to java 11, 17 & 21
for dir in "$GRADLE_DIR"/jdks/*/; do
  major_version_dir=${dir%*/}
  major_version=$(basename "$major_version_dir")
  if [ "$major_version" != "11" ] && [ "$major_version" != "17" ] && [ "$major_version" != "21" ]; then
    continue
  fi
  distribution_local_path=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/local-path)
  jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
  echo "check me"
  echo ln -s "$jdk_installation_directory" "/opt/java${major_version}"
done

cleanup
