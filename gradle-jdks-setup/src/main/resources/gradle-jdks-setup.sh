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
# Set pipefail if it works in a subshell, disregard if unsupported
# shellcheck disable=SC3040
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

APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit
APP_HOME=${APP_HOME%/gradle}
APP_GRADLE_DIR="$APP_HOME"/gradle

# Loading gradle jdk functions
. "$APP_GRADLE_DIR"/gradle-jdks-functions.sh

install_and_setup_jdks "$APP_GRADLE_DIR"

gradle_daemon_jdk_version=$(read_value "$APP_GRADLE_DIR"/gradle-daemon-jdk-version)
gradle_daemon_jdk_distribution_local_path=$(read_value "$APP_GRADLE_DIR"/jdks/"$gradle_daemon_jdk_version"/"$OS"/"$ARCH"/local-path)
"$GRADLE_JDKS_HOME"/"$gradle_daemon_jdk_distribution_local_path"/bin/java -cp "$APP_GRADLE_DIR"/gradle-jdks-setup.jar com.palantir.gradle.jdks.setup.GradleJdkInstallationSetup daemonSetup "$APP_HOME" "$GRADLE_JDKS_HOME/$gradle_daemon_jdk_distribution_local_path"

# [Used by ./gradlew only] Setting the Gradle Daemon Java Home to the JDK distribution
set -- "-Dorg.gradle.java.home=$GRADLE_JDKS_HOME/$gradle_daemon_jdk_distribution_local_path" "$@"

# Setting JAVA_HOME to the gradle daemon to make sure gradlew uses this jdk for `JAVACMD`
export JAVA_HOME="$GRADLE_JDKS_HOME/$gradle_daemon_jdk_distribution_local_path"

cleanup
