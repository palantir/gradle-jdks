# Gradle Jdk Automanagement

`gradle-jdks` manages now also the JDK used to run Gradle.

This means, when you run `./gradlew <task>` or open a Gradle project in IntelliJ, the correct JDK will be downloaded and used even if you have no JDK preinstalled.

The exact same version of the JDK would run in all environments, minimising differences between them.

The correct root certs (if configured, see [below](todo)) would also be inserted into the JDK so it works OOTB with open source projects/public internet.

## Usage

1. First, you must apply the plugin. In the **root project**, Either use the new plugin syntax:

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

2. Next up comes configuring the JDKs plugin. _**Palantirians:** you probably shouldn't use this setyo directly - either use [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) for OSS or `gradle-jdks-internal` for internal projects which will set everything up for you._

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
3. Next, set the gradle daemon Jdk version in the **root project** build.gradle
```
jdks {
    daemonTarget = <version eg. 11/17/21>
}
```
4. Enable the jdk setup by adding the following to `gradle.properties`:
```
gradle.jdk.setup.enabled=true
```
5. Run the following to configure the Gradle entry points to use the JDK setup:
```bash
./gradlew wrapper 
```
The commands above will run the tasks: 
- `wrapperJdkPatcher` which updates the entryPoints (`./gradlew` and `gradle/gradle-wrapper.jar`) to use the JDK setup
- `generateGradleJdkConfigs` which generates in the project's `gradle/` a list of files and directories that configure the JDK versions and distributions, the certs and `gradle-daemon-jdk-version`. See more about the generated structure of the directories [here](todo). These files will need to be committed to the git repo.


### Gradle JDK Configuration directory structure

The Gradle JDK configuration will be rendered **automatically** by `./gradlew generateGradleJdkConfigs` in a directory format structure as it needs to be easily parsed both from Bash and from Java (see [Entry points](todo))

DO NOT MANUALLY CHANGE THE FILES, it may break the JDK setup workflow. Instead, please update the [JdksExtension](todo).
```
project-root/
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   ├── gradle-wrapper.properties
│   ├── jdks/
│   │   ├── <jdkMajorVersion eg.11>/
│   │   │   ├── <os eg. linux>/
│   │   │   │   ├── <arch eg. aarch64>/
│   │   │   │   │   ├── download-url
│   │   │   │   │   ├── local-path
│   ├── certs/
│   │   ├── Palantir3rdGenRootCa.serial-number
│   ├── gradle-daemon-jdk-version
│   ├── gradle-jdks-setup.sh
│   ├── gradle-jdks-setup.jar
├── subProjects/...
```

- `gradle/gradle-daemon-jdk-version` 
  - contains the Gradle JDK Daemon version rendered from [here](todo)
- `gradle/certs/` 
  - contains a list of certificates that need to be added to the configured JDKs in `gradle/jdks/*`
  - is rendered from [here](todo)
- `gradle/jdks`
  - contains a list of directories in the format `<jdk_major_version>/<os>/<arch>` that contain 2 files: 
    - `download-url` full url path for the jdk, os and arch. Rendered from [here](todo) configured in step 2
    - `local-path` the local name of the file. Rendered based on [here](todo)
  - if [`com.palantir.baseline-java-versions` (another gradle plugin - more docs in link)](https://github.com/palantir/gradle-baseline#compalantirbaseline-java-versions) is configured, then it only generates the JDK versions configured by the [`javaVersions` & `javaVersion` extensions](https://github.com/palantir/gradle-baseline#compalantirbaseline-java-versions)
  - otherwise it generates all the JDK versions configured in [here](todo)


## How it works ?

### Entry points
There are 2 main entry points for running Gradle. Both of these would need to support installing/using the specified JDK. 

* `./gradlew(.bat)` script
* Running a Gradle build from inside `Intellij`



