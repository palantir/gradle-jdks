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
#   (1) Downloads the corresponding JDK distribution based on the majorVersion=`gradle/gradle-jdk-major-version`
#     and the distribution_url=`gradle/jdks/${majorVersion}/${os}/${arch}/download_url`
#   (2) Installs the distribution in a temporary directory
#   (3) Calls the java class `GradleJdkInstallationSetup` that will move the distribution to
#   `$GRADLE_USER_HOME/${local_path}` based on the local_path=`gradle/jdks/${majorVersion}/${os}/${arch}/local_path`
#   and it will set up the certificates based on `gradle/certs` entries for the locally installed distribution
#   (4) Sets up the JAVA_HOME and PATH env variables to the currently installed JDK
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

tmp_work_dir=$(mktemp -d)
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME"/.gradle}
GRADLE_JDKS_HOME="$GRADLE_USER_HOME"/gradle-jdks

die () {
    echo
    echo "$*"
    echo
    rm -rf "$tmp_work_dir"
    exit 1
} >&2

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

read -r major_version < "$APP_HOME"/gradle-jdk-major-version
read -r distribution_url < "$APP_HOME"/jdks/"$major_version"/"$os_name"/"$arch_name"/download-url
read -r distribution_local_path < "$APP_HOME"/jdks/"$major_version"/"$os_name"/"$arch_name"/local-path
certs_directory="$APP_HOME"/certs

# Check if distribution exists in $GRADLE_JDKS_HOME
jdk_installation_directory="$GRADLE_JDKS_HOME"/"$distribution_local_path"
if [ -d "$jdk_installation_directory" ]; then
  echo "Distribution $distribution_url already exists, setting JAVA_HOME to $jdk_installation_directory"
else
  mkdir -p "$GRADLE_JDKS_HOME"
  # Download and extract the distribution into a temporary directory
  echo "Distribution $distribution_url does not exist, installing in progress ..."
  in_progress_dir="$tmp_work_dir/$distribution_local_path.in-progress"
  mkdir -p "$in_progress_dir"
  cd "$in_progress_dir"
  if command -v curl > /dev/null 2>&1; then
    echo "Using curl to download $distribution_url"
    curl -C - "$distribution_url" | tar -xzf -
  elif command -v wget > /dev/null 2>&1; then
    echo "Using wget to download $distribution_url"
    wget -qO- -c "$distribution_url" | tar -xzf -
  else
    # TODO(crogoz): fallback to java if it exists
    die "ERROR: Neither curl nor wget are installed, Could not set up JAVA_HOME"
  fi
  cd - || exit

  # Finding the java_home
  java_bin=$(find "$in_progress_dir" -type f -name "java" -path "*/bin/java" ! -type l)
  java_home="${java_bin%/*/*}"
  "$java_home/bin/java" -cp "$APP_HOME"/jdks/gradle-jdks-setup.jar com.palantir.gradle.jdks.setup.GradleJdkInstallationSetup "$jdk_installation_directory" "$certs_directory"
  echo Successfully installed JDK distribution, setting JAVA_HOME to "$jdk_installation_directory"
fi

rm -rf "$tmp_work_dir"

export JAVA_HOME="$jdk_installation_directory"
export PATH=$PATH:$JAVA_HOME/bin
