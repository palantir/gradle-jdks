# Running GradlePluginTests with specific JDKs

## Overview
Use the `@WithJdkAutomanagement` annotation when your tests need to run Gradle builds with specific JDK versions. This annotation can be applied to:
- An entire test class
- Individual test methods

## What it does
The annotation automatically:
1. Adds the required plugins: `com.palantir.jdks` and `com.palantir.jdks.settings`
2. Enables [Gradle JDK Automanagement](https://github.com/palantir/gradle-jdks/tree/develop/gradle-jdks-setup#gradle-jdk-automanagement)
3. Ensures your builds use only the JDKs you've explicitly configured (either by manually specifying them in the `jdks` extension or by using the `com.palantir.jdks.latest` plugin)

## Usage example

```java
@Test
@WithJdkAutomanagement
void can_run_with_daemon_21(GradleInvoker gradle, RootProject project) {
    // Step 1: Configure JDK daemon target
    project.buildGradle().append("""
        jdks {
            daemonTarget = 21
        }
        """);
    
    // Option A: Manually specify JDKs
    project.buildGradle().append("""
        jdks {
            jdk(21) {
                distribution = 'amazon-corretto'
                jdkVersion = '21.0.16.9.1'    
            }
        }
        """);
        
    // Option B: Or use the latest JDKs plugin
    project.buildGradle().plugins().add("com.palantir.jdks.latest");
            
    // Step 2: Configure Java toolchains
    
    // Option A: Direct toolchain configuration
    project.buildGradle().append("""
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(17)
            }
        }
        """);
        
    // Option B: Using baseline-java-versions plugin
    project.buildGradle().plugins().add("com.palantir.baseline-java-versions");
    project.buildGradle().append("""
        javaVersions {
            libraryTarget = 11
        }
        """);
}
```

Make sure that all the gradle plugins used in the tests are set up in the `build.gradle` of the plugin project. More details on this [here](#testing-with-external-plugins)
```groovy
dependencies {
    // these 2 dependencies should always be added:
    gradlePluginForTesting "com.palantir.gradle.jdks:gradle-jdks"
    gradlePluginForTesting "com.palantir.gradle.jdks:gradle-jdks-settings"
    // and optionally, dependending on which extra plugins were used:
    // gradlePluginForTesting "com.palantir.gradle.jdkslatest:gradle-jdks-latest"
    // gradlePluginForTesting "com.palantir.baseline:gradle-baseline-java"
}
```