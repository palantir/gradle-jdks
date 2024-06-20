#!/bin/sh
#
# (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle jdk set up script for POSIX generated by gradle-jdks.
#
#   This script does the following:
#   (1) Downloads all the JDK distributions that are present in `gradle/jdks`
#   (2) Installs the distributions in a temporary directory
#   (3) Calls the java class `GradleJdkInstallationSetup` that will move each distribution to
#   `$GRADLE_USER_HOME/${local_path}` based on the local_path=`gradle/jdks/${majorVersion}/${os}/${arch}/local_path`
#   and it will set up the certificates based on `gradle/certs` entries for the locally installed distribution
#   (4) Sets `org.gradle.java.home` to the JDK distribution that is used by the Gradle Daemon
#
#
#   Important for running:
#   This script requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command» and «set».
#
##############################################################################

set -e
if (set -o  pipefail 2>/dev/null); then
    set -o pipefail
fi

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit
APP_HOME=${APP_HOME%/gradle}
APP_GRADLE_DIR="$APP_HOME"/gradle

tmp_work_dir=$(mktemp -d)
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME"/.gradle}
GRADLE_JDKS_HOME="$GRADLE_USER_HOME"/gradle-jdks
mkdir -p "$GRADLE_JDKS_HOME"

die () {
    echo
    echo "$*"
    echo
    rm -rf "$tmp_work_dir"
    exit 1
} >&2

read_value() {
  if [ ! -f "$1" ]; then
    die "ERROR: $1 not found, aborting Gradle JDK setup"
  fi
  read -r value < "$1" || die "ERROR: Unable to read value from $1. Make sure the file ends with a newline."
  echo "$value"
}

# OS specific support; same as gradle-jdks:com.palantir.gradle.jdks.CurrentOs.java
case "$( uname )" in                          #(
  Linux* )          os_name="linux"  ;;       #(
  Darwin* )         os_name="macos"  ;;       #(
  * )               die "ERROR Unsupported OS: $( uname )" ;;
esac

if [ "$os_name" = "linux" ]; then
    ldd_output=$(ldd --version 2>&1 || true)
    if echo "$ldd_output" | grep -qi glibc; then
       os_name="linux-glibc"
    elif echo "$ldd_output" | grep -qi "gnu libc"; then
           os_name="linux-glibc"
    elif echo "$ldd_output" | grep -qi musl; then
      os_name="linux-musl"
    else
      die "Unable to determine glibc or musl based Linux distribution: ldd_output: $ldd_output"
    fi
fi

# Arch specific support, see: gradle-jdks:com.palantir.gradle.jdks.CurrentArch.java
case "$(uname -m)" in                         #(
  x86_64* )       arch_name="x86-64"  ;;      #(
  x64* )          arch_name="x86-64"  ;;      #(
  amd64* )        arch_name="x86-64"  ;;      #(
  arm64* )        arch_name="aarch64"  ;;     #(
  arm* )          arch_name="aarch64"  ;;     #(
  aarch64* )      arch_name="aarch64"  ;;     #(
  x86* )          arch_name="x86"  ;;         #(
  i686* )         arch_name="x86"  ;;         #(
  * )             die "ERROR Unsupported architecture: $( uname -m )" ;;
esac

for dir in "$APP_GRADLE_DIR"/jdks/*/; do
  major_version_dir=${dir%*/}
  certs_directory="$APP_GRADLE_DIR"/certs
  distribution_local_path=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/local-path)
  distribution_url=$(read_value "$major_version_dir"/"$os_name"/"$arch_name"/download-url)
  # Check if distribution exists in $GRADLE_JDKS_HOME
  jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
  if [ -d "$jdk_installation_directory" ]; then
    echo "Distribution '$distribution_url' already exists in '$jdk_installation_directory'"
  else
    # Download and extract the distribution into a temporary directory
    echo "JDK installation '$jdk_installation_directory' does not exist, installing '$distribution_url' in progress ..."
    in_progress_dir="$tmp_work_dir/$distribution_local_path.in-progress"
    mkdir -p "$in_progress_dir"
    cd "$in_progress_dir"
    if command -v curl > /dev/null 2>&1; then
      echo "Using curl to download $distribution_url"
      case "$distribution_url" in
        *.zip)
          distribution_name=${distribution_url##*/}
          curl -C - "$distribution_url" -o "$distribution_name"
          tar -xzf "$distribution_name"
          ;;
        *)
          curl -C - "$distribution_url" | tar -xzf -
          ;;
      esac
    elif command -v wget > /dev/null 2>&1; then
      echo "Using wget to download $distribution_url"
      case "$distribution_url" in
        *.zip)
          distribution_name=${distribution_url##*/}
          wget -c "$distribution_url" -O "$distribution_name"
          tar -xzf "$distribution_name"
          ;;
        *)
          wget -qO- -c "$distribution_url" | tar -xzf -
          ;;
      esac
    else
      die "ERROR: Neither curl nor wget are installed, Could not set up JAVA_HOME"
    fi
    cd - || exit

    # Finding the java_home
    java_bin=$(find "$in_progress_dir" -type f -name "java" -path "*/bin/java" ! -type l)
    java_home="${java_bin%/*/*}"
    "$java_home"/bin/java -cp "$APP_GRADLE_DIR"/gradle-jdks-setup.jar com.palantir.gradle.jdks.setup.GradleJdkInstallationSetup jdkSetup "$jdk_installation_directory" "$certs_directory" || die "Failed to set up JDK $jdk_installation_directory"
    echo "Successfully installed JDK distribution in $jdk_installation_directory"
  fi
done

gradle_daemon_jdk_version=$(read_value "$APP_GRADLE_DIR"/gradle-daemon-jdk-version)
gradle_daemon_jdk_distribution_local_path=$(read_value "$APP_GRADLE_DIR"/jdks/"$gradle_daemon_jdk_version"/"$os_name"/"$arch_name"/local-path)
"$GRADLE_JDKS_HOME"/"$gradle_daemon_jdk_distribution_local_path"/bin/java -cp "$APP_GRADLE_DIR"/gradle-jdks-setup.jar com.palantir.gradle.jdks.setup.GradleJdkInstallationSetup daemonSetup "$APP_HOME" "$GRADLE_JDKS_HOME/$gradle_daemon_jdk_distribution_local_path"

rm -rf "$tmp_work_dir"

# [Used by ./gradlew only] Setting the Gradle Daemon Java Home to the JDK distribution
set -- "-Dorg.gradle.java.home=$GRADLE_JDKS_HOME/$gradle_daemon_jdk_distribution_local_path" "$@"
