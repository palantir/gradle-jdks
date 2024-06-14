# Gradle Jdk Automanagement

Now `gradle-jdks` manages also the JDK used to run Gradle. This means, when you run `./gradlew <task>` or open a Gradle project in IntelliJ, the correct JDKs will be downloaded and used even if you have no JDK preinstalled. The exact same version of the JDK would run in all environments, minimising differences between them. The correct root certs (if configured, see [below](#gradle-jdk-configuration-directory-structure)) would also be inserted into the JDKs, so it works OOTB with open source projects/public internet.

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
2. Next up comes configuring the JDKs plugin. _**Palantirians:** you probably shouldn't use this setup directly - either use [`gradle-jdks-latest`](https://github.com/palantir/gradle-jdks-latest) for OSS or `gradle-jdks-internal` for internal projects which will set everything up for you._
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
3. Next, set the gradle daemon major Jdk version in the **root project** build.gradle
```
jdks {
    daemonTarget = <version eg. 11/17/21>
}
```
4. Enable the jdk setup by adding the following to `gradle.properties`:
```
palantir.jdk.setup.enabled=true
```
5. Run the following to configure the Gradle entry points to use the JDK setup:
```bash
./gradlew setupJdks 
```
The commands above will run the tasks: 
- `generateGradleJdkConfigs` which generates in the project's `gradle/` a list of files and directories that configure the JDK versions and distributions, the certs and `gradle-daemon-jdk-version`. See more about the generated structure of the directories [here](#gradle-jdk-configuration-directory-structure). These files will need to be committed to the git repo.
- `wrapperJdkPatcher` which updates the entryPoints (`./gradlew` and `gradle/gradle-wrapper.jar`) to use the JDK setup

6. Run `./gradlew` to install and configure the JDKs and check the configured toolchains:
```
./gradlew javaToolchains
```
The output should look like:
```
$ ./gradlew  javaToolchains
Distribution https://corretto.aws/downloads/resources/11.0.23.9.1/amazon-corretto-11.0.23.9.1-macosx-aarch64.tar.gz already exists in /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4
Distribution https://corretto.aws/downloads/resources/17.0.11.9.1/amazon-corretto-17.0.11.9.1-macosx-aarch64.tar.gz already exists in /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-17.0.11.9.1-f0e4bf13f7416be0
Setting daemon Java Home to /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4
Setting custom toolchains locations to [/Users/crogoz/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4, /Users/crogoz/.gradle/gradle-jdks/amazon-corretto-17.0.11.9.1-f0e4bf13f7416be0]
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
Note that now, when running any `./gradlew` command the first log lines would say that:
* a toolchain is installed already:
```
Distribution 'https://corretto.....' already exists in '/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4'
```
* or that a toolchain needs to be installed:
```
JDK installation '/.gradle/gradle-jdks/amazon-corretto-11.0.23.9.1-d6ef2c62dc4d4dd4' does not exist, installing 'https://corretto.....' in progress ...
```

7. Check the gradle version
```
./gradlew --version
```

The JVM should correspond to the java major version configured in step 3.


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
│   ├── certs/
│   │   ├── Palantir3rdGenRootCa.serial-number
│   ├── gradle-daemon-jdk-version
│   ├── gradle-jdks-setup.sh
│   ├── gradle-jdks-setup.jar
├── subProjects/...
```

- `gradle/gradle-daemon-jdk-version` 
  - contains the Gradle JDK Daemon version rendered from `JdksExtension#daemonTarget`
- `gradle/certs/` 
  - contains a list of certificates (as serial numbers) that need to be added to the configured JDKs in `gradle/jdks/*`.
  - is rendered from `JdksExtension#caCerts`
- `gradle/jdks`
  - contains a list of directories in the format `<jdk_major_version>/<os>/<arch>` that contain 2 files: 
    - `download-url` full url path for the jdk, os and arch. Rendered from `JdksExtension#jdks` configured in step 2
    - `local-path` the local name of the file. Rendered based on the distribution-name, version and the [hash](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdkSpec.java) 
  - it generates all the JDK versions configured in [JdksExtension](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/JdksExtension.java)

Running the patched `./gradlew` script will add extra configurations required for Intelij:
```
project-root/
├── .gradle/
│   ├── config.properties
├── .idea/
│   ├── runConfigurations/
│   │   ├── IntelijGradleJdkSetup.xml
│   ├── startup.xml
```

- `.gradle/config.properties` - sets up `java.home` to the gradle JDK daemon path.
- `.idea/runConfigurations/IntelijGradleJdkSetup.xml` - sets up the Intelij Gradle JDK setup. Patches `.idea/gradle.xml` file (if it exists).
- `.idea/startup.xml` tells Intelij to run the Configuration `IntelijGradleJdkSetup`

Other file changes:
- `gradle.properties` is patched to disable `Auto-detection` and `Auto-download` and sets the `org.gradle.java.installations.paths` to the JDKs installed by the `./gradlew` script.
- `.idea/gradle.xml` if the file exists (it is not a new Intelij plugin), then the `gradleJvm` option is set to `#GRADLE_LOCAL_JAVA_HOME` which is read from `.gradle/config.properties`. If the file doesn't yet exists, it prompts the user to set the configuration manually in `Settings | Build, Execution, Deployment | Build Tools | Gradle | Gradle JVM

## How it works ?
There are 2 main entry points for running Gradle. Both of these would need to support installing/using the specified JDK. 

* `./gradlew(.bat)` script
* Running a Gradle build from inside `Intellij`

### Supporting Gradle JDK auto-management from `./gradlew(.bat)` scripts

The `./gradlew` script is the main entry point for running Gradle builds. It is a shell script that downloads the Gradle wrapper jar and runs it with a JDK. The script is generated by the `gradle-wrapper` plugin and is stored in the `gradle/wrapper` directory of the project.
We are modifying the `./gradlew` script to use the JDK setup by patching it using the scripts: [gradlew-patch.sh](../gradle-jdks/src/main/resources/gradlew-patch.sh) and [gradle-jdks-setup.sh](src/main/resources/gradle-jdks-setup.sh). 
The patching is done by the [`wrapperJdkPatcher` task.](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/GradleWrapperPatcher.java).
The patch script does the following: 
* downloads all the JDKs that are configured in the `gradle/jdks` directory [see above the dirctory structure](#gradle-jdk-configuration-directory-structure) 
* delegates to `gradle-jdks-setup.jar` ([setup class](src/main/java/com/palantir/gradle/jdks/setup/GradleJdkInstallationSetup.java)) the installation of the JDKS and the certs (configured in `gradle/certs`)
* sets the env var `JAVA_HOME` to the installation path of the JDK configured in `gradle/gradle-daemon-jdk-version`. Hence, `./gradlew` will retrieve this java installation and it will run the wrapper using this java installation.


### Running a Gradle build from inside `Intellij`
`Intelij` doesn't use the `./gradlew` script, instead it uses the [Gradle Tooling API](https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_introduction).
In order to do the Gradle JDK setup in Intelij as well, we are introducing a [Startup Task](https://www.jetbrains.com/help/idea/settings-tools-startup-tasks.html) that will run [IntelijGradleJdkSetup](src/main/java/com/palantir/gradle/jdks/setup/IntelijGradleJdkSetup.java) which will install the JDKs & configure the Intelij Gradle JVM.

## ToolchainsPlugin tasks

The new workflow is set up by [ToolchainsPlugin](../gradle-jdks/src/main/java/com/palantir/gradle/jdks/ToolchainsPlugin.java) which gets applied if `palantir.jdk.setup.enabled=true`.
The plugin won't apply anymore the `baseline-java-versions` plugin, allowing for the configuration of the Java Toolchains as described in the [Gradle docs ](https://docs.gradle.org/current/userguide/toolchains.html)

The plugin registers the following tasks:
- `wrapperPatcherTask` - finalizes the `wrapper` task, such that everytime the `gradle-wrapper.jar` and/or `./gradlew` files are updated, we will also patch them
- `checkWrapperPatcher` - checks that the `./gradlew` script contains the expected JDKs setup patch
- `generateGradleJdkConfigs` - generates the [`gradle/` configurations](#gradle-jdk-configuration-directory-structure) required for running the JDKs setup
- `checkGradleJdkConfigs` - checks that all the `gradle/` configurations are up-to-date. E.g. if the `jdks-latest` plugin is updated, we need to make sure the `gradle/jdks` files reflect the jdk versions.
- `setupJdks` - lifecycle task that runs both the `wrapperPatcherTask` and `generateGradleJdkConfigs`


## Unsupported

- This workflow is disabled on `Windows` at the moment.
- We only support Java language Versions specifications >= 11 (see `gradle-jdks-setup/build.gradle` `javaVersion` specifications).
