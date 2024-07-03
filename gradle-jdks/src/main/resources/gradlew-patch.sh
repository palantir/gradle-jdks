# >>> Gradle JDK setup >>>
# !! Contents within this block are managed by 'palantir/gradle-jdks' !!
if [ -f gradle/gradle-jdks-setup.sh ]; then
    if ! . gradle/gradle-jdks-setup.sh; then
        echo "Failed to set up JDK, running gradle/gradle-jdks-setup.sh failed with non-zero exit code"
        exit 1
    fi
fi
# <<< Gradle JDK setup <<<
