#!/bin/bash

set -eux

die () {
    echo
    echo "$*"
    echo
    rm -rf "$tmp_work_dir"
    exit 1
} >&2

read_value() {
  if [ ! -f "$1" ]; then
    die "ERROR: $1 not found, aborting Gradle JDK installation"
  fi
  read -r value < "$1" || die "ERROR: Unable to read value from $1. Make sure the file ends with a newline."
  echo "$value"
}

GRADLE_JDKS_DIR=$1
CERTS_DIR=$2

if [[ -z "$CERTS_DIR" || -z "$GRADLE_JDKS_DIR" ]]; then
  die "ERROR: Missing argument CERTS_DIR"
fi

GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME"/.gradle}
GRADLE_JDKS_HOME="$GRADLE_USER_HOME"/gradle-jdks
mkdir -p "$GRADLE_JDKS_HOME"

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

for dir in /jdks/latest-gradle-jdks/*/; do
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
  java_bin=$(find "$jdk_installation_directory" -type f -name "java" -path "*/bin/java" ! -type l -print -quit)
  java_home="${java_bin%/*/*}"
  # Add Java truststore
  for cert_path in "$CERTS_DIR"/Palantir*; do
    cert_name=$(basename "$cert_path")
    echo "Adding ${cert_name} to ${java_home} truststore"
    "$java_home"/bin/keytool -import -trustcacerts -file "$cert_path" -alias "$cert_name" -keystore "$java_home"/lib/security/cacerts -storepass changeit -noprompt
  done
done
