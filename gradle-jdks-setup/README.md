# Gradle Jdk Automanagement

`gradle-jdks` manages now also the JDK used to run Gradle. This means, when you run `./gradlew <task>` or open a Gradle project in IntelliJ (using the `palantir-gradle-jdk` Intellij plugin), the correct JDKs will be downloaded and used even if you have no JDK preinstalled. The exact same version of the JDK would run in all environments. The system root certs would also be inserted into the JDKs' truststore.

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
2. We need to apply the com.palantir.jdks.settings plugin in **settings.gradle** file:
```gradle
  buildscript {
      repositories {
          mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
          gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
          mavenLocal()
      }
  
      dependencies {
          classpath 'com.palantir.gradle.jdks:gradle-jdks-settings:<latest-version>'
      }
  }
  
  apply plugin: 'com.palantir.jdks.settings'
```
3. Next up comes configuring the JDKs plugin. _**Palantirians:** you probably shouldn't use this setup directly - either use [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) for OSS or `gradle-jdks-internal` for internal projects which will set everything up for you._
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
   
   // Optional: Where to store the JDKs on disk. You almost certainly
   //           do not need to change this. 
   // Default:  $HOME/.gradle/gradle-jdks
   jdkStorageLocation = System.getProperty("user.home") + '/custom/location'
}
```
4. Next, set the gradle daemon major Jdk version in the **root project** build.gradle
```
jdks {
    daemonTarget = <version eg. 11/17/21>
}
```
5. Enable the jdk setup by adding the following to `gradle.properties`:
```
palantir.jdk.setup.enabled=true
```
6. Run the following to configure the Gradle entry points to use the JDK setup:
```bash
./gradlew setupJdks 
```
The commands above will trigger the tasks: 
- `generateGradleJdkConfigs` which generates in the project's `gradle/` a list of files and directories that configure the JDK versions and distributions and `gradle-daemon-jdk-version`. See more about the generated structure of the directories [here](#gradle-jdk-configuration-directory-structure). These files will need to be committed to the git repo.
- `wrapperJdkPatcher` which patches the `./gradlew` script to run `./gradle/gradle-jdks-setup.sh`
- `setupJdks` is calling the patched `./gradlew(.bat) javaToolchains` script to install and configure the JDKs and check the configured toolchains:

The output should look like:
```
Starting a Gradle Daemon (subsequent builds will be faster)

> Task :javaToolchains

+ Options
  | Auto-detection:     Disabled
  | Auto-download:      Disabled

+ Amazon Corretto JDK 11.0.23+9-LTS
  | Location:           /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4
  | Language Version:   11
  | Vendor:             Amazon Corretto
  | Architecture:       aarch64
  | Is JDK:             true
  | Detected by:        Gradle property 'org.gradle.java.installations.paths'

+ Amazon Corretto JDK 17.0.11+9-LTS
  | Location:           /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-17.0.11.9.1-f0e4bf13f7416be0
  | Language Version:   17
  | Vendor:             Amazon Corretto
  | Architecture:       aarch64
  | Is JDK:             true
  | Detected by:        Gradle property 'org.gradle.java.installations.paths'

```
Note that `Auto-detection` and `Auto-download` options should be both disabled and the list of toolchains should only be retrieved from the `Gradle property 'org.gradle.java.installations.paths'`.
Note that now, when running any `./gradlew` the following line would show up if a specific JDK isn't installed on the host:
```
JDK installation '/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4' does not exist, installing 'https://corretto.....' in progress ...
```

7. [Intellij] Install the Intellij plugin [`palantir-gradle-jdks`](https://plugins.jetbrains.com/plugin/24776-palantir-gradle-jdks/versions/stable) and restart Intellij. The plugin will automatically configure the Gradle JVM to use the JDK setup by the `com.palantir.jdks` plugin.

![Intellij Gradle JDK setup](Intellij%20Gradle%20JDK.png)

* Gradle JVM is set to `#GRADLE_LOCAL_JAVA_HOME` which is resolved by Intellij to the `java.home` set in `.gradle/config.properties`.
* The `java.home` is set to the Gradle JDK Daemon resolved path which is resolved from the `gradle/gradle-daemon-jdk-version` file.
* When opening a Project in Intellij with the Gradle JDK setup enabled, a tab will appear at the bottom `Gradle JDK Setup` which will call the `gradle/gradle-jdks-setup.sh` script

## Gradle JDK Configuration directory structure

The Gradle JDK configuration will be rendered **automatically** by `./gradlew generateGradleJdkConfigs` in a directory format structure as it needs to be easily parsed both from Bash and from Java (see [Entry points](#how-it-works-))

DO NOT MANUALLY CHANGE THE FILES, it may break the JDK setup workflow. Instead, please update the [JdksExtension](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdksExtension.java) or update the [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) version.
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
│   ├── gradle-daemon-jdk-version
│   ├── gradle-jdks-setup.sh
│   ├── gradle-jdks-functions.sh
│   ├── gradle-jdks-setup.jar
├── subProjects/...
```

- `gradle/gradle-daemon-jdk-version` 
  - contains the Gradle JDK Daemon version rendered from `JdksExtension#daemonTarget`
- `gradle/jdks`
  - contains a list of directories in the format `<jdk_major_version>/<os>/<arch>` that contain 2 files: 
    - `download-url` full url path for the jdk, os and arch. Rendered from `JdksExtension#jdks` configured in step 2
    - `local-path` the local name of the file. Rendered based on the distribution-name, version and the [hash](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdkSpec.java) 
  - it generates all the JDK versions configured in [JdksExtension](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdksExtension.java)

Running the patched `./gradlew` script will add extra configurations required for IntelliJ:
```
project-root/
├── .gradle/
│   ├── config.properties
```

- `.gradle/config.properties` - sets up `java.home` to the gradle JDK daemon path.

## How it works ?
There are 2 main entry points for running Gradle. Both of these would need to support installing/using the specified JDK. 

* `./gradlew(.bat)` script
* Running a Gradle build from inside `Intellij`

### Supporting Gradle JDK auto-management from `./gradlew` scripts

The `./gradlew` script is the main entry point for running Gradle builds. It is a shell script that downloads the Gradle wrapper jar and runs it with a JDK. The script is generated by the `gradle-wrapper` plugin and is stored in the `gradle/wrapper` directory of the project.
We are modifying the `./gradlew` script to use the JDK setup by patching it using the scripts: [gradlew-patch.sh](../gradle-jdks/src/main/resources/gradlew-patch.sh) and [gradle-jdks-setup.sh](src/main/resources/gradle-jdks-setup.sh). 
The patching is done by the [`wrapperJdkPatcher` task.](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/GradleWrapperPatcher.java).
The patch script does the following: 
* downloads all the JDKs that are configured in the `gradle/jdks` directory [see above the dirctory structure](#gradle-jdk-configuration-directory-structure) 
* delegates to `gradle-jdks-setup.jar` ([setup class](src/main/java/com/palantir/gradle/jdks/setup/GradleJdkInstallationSetup.java)) the installation of the JDKS and the system certs.
* sets the gradle property `org.gradle.java.home` to the installation path of the JDK configured in `gradle/gradle-daemon-jdk-version`. Hence, `./gradlew` will retrieve this java installation and it will run the wrapper using this java installation.


### Running a Gradle build from inside `Intellij`
`IntelliJ` doesn't use the `./gradlew` script, instead it uses the [Gradle Tooling API](https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_introduction).
The Intellij plugin [`palantir-gradle-jdks`](https://plugins.jetbrains.com/plugin/24776-palantir-gradle-jdks/versions/stable) will:
* run the gradle jdks setup script: `./gradle/gradle-jdks-setup.sh` *before* any Gradle Task and *before* the idea project is configured.
* set the Gradle JVM version to the Gradle local java home (`GRADLE_LOCAL_JAVA_HOME` which is resolved from the `gradle/gradle-daemon-jdk-version` file).

## ToolchainsPlugin tasks

The new workflow is set up by [ToolchainsPlugin](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/ToolchainsPlugin.java) which gets applied if `palantir.jdk.setup.enabled=true`.
The plugin won't apply the `baseline-java-versions` plugin anymore, allowing for the configuration of the Java Toolchains as described in the [Gradle docs ](https://docs.gradle.org/current/userguide/toolchains.html)

The plugin registers the following tasks:
- `wrapperJdkPatcher` - finalizes the `wrapper` task such that everytime `./gradlew` file is updated, it will also be patched
- `checkWrapperJdkPatcher` - checks that the `./gradlew` script contains the expected JDKs setup patch
- `generateGradleJdkConfigs` - generates the [`gradle/` configurations](#gradle-jdk-configuration-directory-structure) required for running the JDKs setup
- `checkGradleJdkConfigs` - checks that all the `gradle/` configurations are up-to-date. E.g. if the `jdks-latest` plugin is updated, we need to make sure the `gradle/jdks` files reflect the jdk versions.
- `setupJdks` - task that triggers `wrapperJdkPatcher` and `generateGradleJdkConfigs` and runs the patched `./gradlew` script.

## Unsupported

- This workflow is disabled on `Windows` at the moment.
- We only support Java language Versions specifications >= 11 (see `gradle-jdks-setup/build.gradle` `javaVersion` specifications).
- We only support Gradle versions >= 7.6
