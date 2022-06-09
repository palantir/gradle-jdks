# gradle-jdks

Automatically provision specific versions of JDKs for Gradle tasks that require JDKs (`JavaCompile`, `JavaExec` etc) . Choose from a variety of different JDK vendors which can be automatically installed on multiple OSs.

## Setup

**Note:** Palantirians - you probably shouldn't use this plugin directly - either use `gradle-jdks-latest` for OSS or `gradle-jdks-internal` for non-OSS which will set everything up for you.



## Motivation

Gradle has a built-in concept of [auto-provisioning Java Toolchains](https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning), however it's lacking in some aspects:

1. It always uses AdoptOpenJDK/Adoptium.
   1. This is not our preferred JDK vendor. We use Azul Zulu for [their MTS support](https://www.azul.com/products/azul-support-roadmap/). We would like to develop test with the same JDK we use in prod. 
   2. Adoptium does not provide macos `aarch64` for Java 11, which we need.
2. Once Gradle finds an appropriate JDK on disk for that Java language version, it is always used and never updated.
   1. As a company with tens of millions of lines of Java, we often bump into JVM bugs. It's highly desirable we test and deploy using the exact same version of JDKs.
   2. Similarly, it's important to us that the exact same JDK versions are used on local machines as well as CI.
3. Gradle does not handle JDK CA certificates.
   1. Many companies use a TLS interception when network calls are made outside their corporate network. Gradle provides no way to install the relevant CA certificates into auto-provisioned JDKs to enable working in such an environment.  
4. You cannot easily set up a mirror for auto-provisioned JDK downloads.
   1. There's a Gradle property you can set to change to server base uri, but this still leaves you writing and maintaining a service to serve the adoptium api.
   2. An internal corporate mirror can be 100x faster than a public one, especially for CI builds.

`gradle-jdks` solves all of theses problems.


