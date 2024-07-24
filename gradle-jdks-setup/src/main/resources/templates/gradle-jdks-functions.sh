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
