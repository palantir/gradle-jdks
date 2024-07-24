#!/bin/bash

set -eux

############################### Gradle JDK Functions #########################

read_value() {
  if [ ! -f "$1" ]; then
    die "ERROR: $1 not found, aborting Gradle JDK setup"
  fi
  read -r value < "$1" || die "ERROR: Unable to read value from $1. Make sure the file ends with a newline."
  echo "$value"
}


get_os() {
  # OS specific support; same as gradle-jdks:com.palantir.gradle.jdks.setup.common.CurrentOs.java
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

  echo "$os_name"
}


get_arch() {
  # Arch specific support, see: gradle-jdks:com.palantir.gradle.jdks.setup.common.CurrentArch.java
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

  echo "$arch_name"
}


get_gradle_jdks_home() {
  gradle_user_home=${GRADLE_USER_HOME:-"$HOME"/.gradle}
  gradle_jdks_home="$gradle_user_home"/gradle-jdks
  echo "$gradle_jdks_home"
}


get_java_home() {
  java_bin=$(find "$1" -type f -name "java" -path "*/bin/java" ! -type l -print -quit)
  echo "${java_bin%/*/*}"
}

##############################################################################


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
