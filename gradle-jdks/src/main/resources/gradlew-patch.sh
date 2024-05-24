# >>> Gradle JDK setup >>>
# TODO(crogoz): what if we disable the gradle.jdk.setup.enabled=false?
# !! Contents within this block are managed by 'palantir/gradle-jdks' !!
if [ -f gradle/gradle-jdks-setup.sh ]; then
    if ! . gradle/gradle-jdks-setup.sh; then
        echo "Failed to set up JDK, running gradle/gradle-jdks-setup.sh failed with non-zero exit code"
        exit 1
    fi
    # Set the flag to indicate that the JDK setup is done from gradlew
    set -- "$@" '-Drunning.from.gradlew=true'
fi
# <<< Gradle JDK setup <<<
