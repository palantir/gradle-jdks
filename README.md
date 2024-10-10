<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-jdks"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-jdks

Automatically provision specific versions of JDKs for Gradle tasks that require JDKs (`JavaCompile`, `Test`, `JavaExec` etc) . Choose from a variety of different JDK vendors which can be automatically installed on multiple OSs.

## Motivation

Gradle has a built-in concept of [auto-provisioning Java Toolchains](https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning), which will automatically install JDKs if they do not exist on system running Gradle, however it's lacking in some aspects:

1. **Gradle always uses AdoptOpenJDK/Adoptium.**
   1. You may prefer a different JDK vendor. We use Azul Zulu for [their MTS support](https://www.azul.com/products/azul-support-roadmap/). It is important to us to develop and test with the same JDK we use in prod. 
   2. Adoptium does not provide macos `aarch64` for Java 11, which we need for Apple Silicon Macbooks.
2. **Once Gradle finds an appropriate JDK on disk for that Java language version, it is always used and never updated.**
   1. As a company with tens of millions of lines of Java, we often bump into JVM bugs. It's highly desirable we test and deploy using the exact same version of JDKs.
   2. Similarly, it's important to us that the exact same JDK versions are used on local machines as well as CI.
3. **Gradle does not handle JDK CA certificates.**
   1. Many companies use TLS interception when network calls are made outside their corporate network. Gradle provides no way to install the relevant CA certificates into auto-provisioned JDKs. This means they cannot be used in such an environment, or require manual patching.  
4. **You cannot easily set up a mirror for auto-provisioned JDK downloads.**
   1. There's a [Gradle property you can set to change to server base uri](https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning:~:text=org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri), but this still leaves you writing and maintaining a service that replicates the adoptium api.
   2. An internal corporate mirror can be 100x faster than a public one, especially for CI builds.

`gradle-jdks` solves all of these problems:

1. **You can choose your favoured JDK vendor.**
2. **Use the same JDK version for representative, reproducible builds on dev machines and in CI.**
3. **Automatically add JDK CA certificates.**
4. **Point to an internal mirror for JDKs.**
5. [Gradle JDK Automanagement] **Configures the Gradle Daemon JDK and the Toolchains used**

## Usage

_**Palantirians:** you probably shouldn't use this plugin directly - either use [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) for OSS or `gradle-jdks-internal` for internal projects which will set everything up for you._

First, you must apply the plugin. In the **root project**, Either use the new plugin syntax:

```gradle
plugins {
   id 'com.palantir.jdks' version '<latest version>'
}
```

or the old buildscript method:

```gradle
buildscript {
   repositories {
      mavenCentral()
   }
   
   dependencies {
      classpath 'com.palantir.gradle.jdks:gradle-jdks:<latest-version>'
   }
}

apply plugin: 'com.palantir.jdks'
```

Next up comes configuring the JDKs plugin:

```gradle
jdks {
   // Required: For each Java major version you use, you need to specify
   //           a distribution and version.
   jdk(11) {
      distribution = 'azul-zulu'
      jdkVersion = '11.54.25-11.0.14.1'
   }
   
   jdk(17) {
      distribution = 'amazon-corretto'
      jdkVersion = '17.0.3.6.1'
   }
   
   // You can also individually specify different versions per OS and arch
   jdk(21) {
      distribution = 'amazon-corretto'
      jdkVersion = '21.0.2.13.1'
      
      // OS options are linux-glibc, linux-musl, macos, windows
      os('linux-glibc') {
         jdkVersion = '21.0.2.14.1'
       
         // arch options are x86, x86-64, aarch64
         arch('aarch64') {
            jdkVersion = '21.0.2.14.2'
         }     
      }
   }
   
   // Optional: For each distribution, you can set a base url for a
   //           mirror to use instead of the default public mirror.
   // Default:  Whatever mirror is publicly provided by the vendor
   jdkDistribution('azul-zulu') {
      baseUrl = 'https://internal-corporate-mirror/azul-zulu-cdn-mirror'
   }
   
   // [Ignored by the Gradle JDK Automanagement workflow]
   // Optional: You can specify CA certs which will be installed into
   //           the extracted JDK to work with TLS interception.
   // Default:  No CA certs are added.
   caCerts.put 'corporate-tls-cert', '''
      -----BEGIN CERTIFICATE-----
      // snip
      -----END CERTIFICATE-----
   '''.stripIndent(true)
   
   // Optional: Where to store the JDKs on disk. You almost certainly
   //           do not need to change this. 
   // Default:  $HOME/.gradle/gradle-jdks
   jdkStorageLocation = System.getProperty("user.home") + '/custom/location'
}
```

Behind the scenes, `gradle-jdks` applies [`com.palantir.baseline-java-versions` (another gradle plugin - more docs in link)](https://github.com/palantir/gradle-baseline#compalantirbaseline-java-versions) to handle configuring the Java language versions. **You will need to configure this plugin as well** to tell it what Java language versions:

```gradle
// Read the docs at https://github.com/palantir/gradle-baseline#compalantirbaseline-java-versions
javaVersions {
   libraryTarget = 11
   distributionTarget = 17
}
```

## What JDK distributions are supported?

[**Supported JDK distribution can be found here.**](https://github.com/palantir/gradle-jdks/blob/develop/gradle-jdks-distributions/src/main/java/com/palantir/gradle/jdks/JdkDistributionName.java#L26)

New JDK distributions are easily added - you just need to:
1. Add an entry to [`JdkDistributionName`](https://github.com/palantir/gradle-jdks/blob/develop/gradle-jdks-distributions/src/main/java/com/palantir/gradle/jdks/JdkDistributionName.java#L26)
2. Add a `JdkDistribution` - [Azul Zulu example](https://github.com/palantir/gradle-jdks/blob/develop/gradle-jdks/src/main/java/com/palantir/gradle/jdks/AzulZuluJdkDistribution.java).
3. Add the JDK distribution [here](https://github.com/palantir/gradle-jdks/blob/develop/gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdkDistributions.java#L22).
4. Write some tests to check the path is being built correctly - [Azul Zulu example](https://github.com/palantir/gradle-jdks/blob/develop/gradle-jdks/src/test/groovy/com/palantir/gradle/jdks/AzulZuluJdkDistributionTest.java).
5. Make a PR.

## [Feature flagged][Gradle JDK Automanagement] Run the Gradle wrapper/daemon with a certain JDK
   * More details in [grade-jdks-setup](gradle-jdks-setup/README.md)
   * Disabled by default for now

## How can I see what JDK tasks are running with?

If you run you gradle invocation with `--info --rerun-tasks`, the JDK will be visible in the output:

```
$ ./gradlew compileJava --info --rerun-tasks
> Task :my-project:compileJava
Compiling with toolchain '/Users/username/.gradle/gradle-jdks/azul-zulu-17.34.19-17.0.3-a3ceab47882436a6'.
```

## Related projects

* [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) applies this plugin and configures the latest JDK releases - primarily exists for Palantir use, enforcing consistency across our open-source projects.
