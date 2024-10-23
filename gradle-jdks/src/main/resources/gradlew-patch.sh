# >>> Gradle JDK setup >>>
# !! Contents within this block are managed by 'palantir/gradle-jdks' !!
if [ -f gradle/gradle-jdks-setup.sh ]; then
    . gradle/gradle-jdks-setup.sh
    exit_status=$?
    case $exit_status in
        0) JAVA_HOME="$GRADLE_DAEMON_JDK_HOME"; echo "Successful Gradle JDK setup. Using java_home=$JAVA_HOME.";;
        65) echo "Skipping the Gradle JDK setup due to non-supported os/arch"; exit $exit_status;;
        *) echo "Failed Gradle JDK setup, running gradle/gradle-jdks-setup.sh failed with non-zero exit code"; exit $exit_status;;
    esac
fi
# <<< Gradle JDK setup <<<
