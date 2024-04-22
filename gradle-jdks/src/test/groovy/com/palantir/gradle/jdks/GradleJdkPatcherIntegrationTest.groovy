/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.jdks

import static org.assertj.core.api.Assertions.assertThat

import spock.lang.TempDir
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import nebula.test.IntegrationSpec
import java.nio.file.Files
import java.nio.file.Path

class GradleJdkPatcherIntegrationTest extends IntegrationSpec {

    private static AmazonCorrettoJdkDistribution CORRETTO_JDK_DISTRIBUTION = new AmazonCorrettoJdkDistribution();
    private static String GRADLE_7VERSION = "7.6.2"
    private static String GRADLE_8VERSION = "8.5"
    private static String JDK_11_VERSION = "11.0.22.7.1"
    private static String JDK_17_VERSION = "17.0.9.8.1"
    private static String JDK_21_VERSION = "21.0.2.13.1"
    private static String AMAZON_ROOT_CA_1_SERIAL = "143266978916655856878034712317230054538369994"
    private static String PALANTIR_3RD_GEN_SERIAL = "18126334688741185161"
    private static final int JAVA_17_BYTECODE = 61
    private static final int ENABLE_PREVIEW_BYTECODE = 65535

    @TempDir
    private Path workingDir

    def java17PreviewCode = '''
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }
        
            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                switch (myUnion) {
                    case MyUnion.Foo foo -> System.out.println("Java 17 pattern matching switch: " + foo.number);
                }
            }
        }
        '''

    def setup() {

        // language=groovy
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
                // we need to inject the classpath of the plugin under test manually. The tests call the `./gradlew` 
                // command directly in the tests (so not using the nebula-test workflow).
                dependencies {
                    classpath files(FILES)
                }
            }
            apply plugin: 'java'
            apply plugin: 'com.palantir.jdks'
            apply plugin: 'application'

            tasks.register('getGradleJavaHomeProp') {
                doLast {
                    println "Gradle java home is " + System.getProperty('org.gradle.java.home')
                }
            }
            
            application {
                mainClass = 'Main'
            }
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)
    }

    private Iterable<File> getPluginClasspathInjector() {
        File propertiesFile = new File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties")
        Properties properties = new Properties()
        propertiesFile.withInputStream { inputStream ->
            properties.load(inputStream)
        }
        String classpath = properties.getProperty('implementation-classpath')

        return classpath.split(File.pathSeparator).collect { "'" + it + "'" }
    }


    def '#gradleVersionNumber: patches gradleWrapper to set up JDK'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper', 'getGradleJavaHomeProp')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        !output.wasSkipped(':wrapperJdkPatcher')
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        output.standardOutput.contains("Gradle java home is null")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")

        when:
        String wrapperResult1 = upgradeGradleWrapper()
        String wrapperResult2 = upgradeGradleWrapper()

        then:
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        String expectedLocalPath = gradleJdksPath.resolve(getLocalFilename(JDK_17_VERSION))
        wrapperResult1.contains(String.format("Successfully installed JDK distribution in %s", expectedLocalPath))
        String expectedJdkLog = "JVM:          17.0.9 (Amazon.com Inc. 17.0.9+8-LTS)"
        wrapperResult1.contains(expectedJdkLog)
        wrapperResult1.contains("Gradle 7.6.2")
        wrapperResult1.contains("Gradle java home is " + expectedLocalPath)
        file('gradle/wrapper/gradle-wrapper.properties').text.contains("gradle-8.4-bin.zip")
        wrapperResult2.contains(String.format("already exists in %s", expectedLocalPath))
        wrapperResult2.contains(expectedJdkLog)
        wrapperResult2.contains("Gradle 8.4")
        wrapperResult2.contains("Gradle java home is " + expectedLocalPath)

        where:
        gradleVersionNumber << [ GRADLE_7VERSION ]
    }

    def '#gradleVersionNumber: javaToolchains correctly set-up'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'

        buildFile << '''
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java17PreviewCode

        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_11_VERSION, Set.of(JDK_11_VERSION, JDK_17_VERSION))

        //language=groovy
        def subprojectLib = addSubproject 'subprojectLib', '''
            apply plugin: 'java-library'
            javaVersion {
                library()
            }
        '''.stripIndent(true)

        writeHelloWorld(subprojectLib)

        when:
        runTasksSuccessfully('wrapper').standardOutput
        String output = runGradlewCommand(List.of("./gradlew", "javaToolchains", "compileJava", "--debug"))
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        output.contains("Auto-detection:     Disabled")
        output.contains("Auto-download:      Disabled")
        output.contains("JDK 11.0.22")
        output.contains("JDK 17.0.9")
        Matcher matcher = Pattern.compile("Detected by:       (.*)").matcher(output)
        while (matcher.find()) {
            String detectedByPattern = matcher.group(1)
            detectedByPattern.contains("Gradle property 'org.gradle.java.installations.paths'")
        }
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        Path expectedJdk11 = gradleJdksPath.resolve(getLocalFilename(JDK_11_VERSION).trim())
        output.contains(String.format("Compiling with toolchain '%s'", expectedJdk11.toFile().getCanonicalPath()))
        Path expectedJdk17 = gradleJdksPath.resolve(getLocalFilename(JDK_17_VERSION).trim())
        output.contains(String.format("Compiling with toolchain '%s'", expectedJdk17.toFile().getCanonicalPath()))
        output.contains("--enable-preview")
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE)

        when:
        String runOutput = runGradlewCommand(List.of("./gradlew", "run", "-i"))

        then:
        runOutput.contains("--enable-preview")
        runOutput.contains(expectedJdk17.toFile().getCanonicalPath())
        runOutput.contains("BUILD SUCCESSFUL")

        where:
        gradleVersionNumber << [ GRADLE_8VERSION ]
    }

    def '#gradleVersionNumber: fails if toolchain not found'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_17_VERSION, Set.of(JDK_17_VERSION, JDK_21_VERSION))
        def subprojectLib = addSubproject 'subprojectLib', '''
            apply plugin: 'java-library'
        '''.stripIndent(true)

        writeHelloWorld(subprojectLib)

        when:
        runTasksSuccessfully('wrapper').standardOutput
        String output = runGradlewCommand(List.of("./gradlew", "javaToolchains", "compileJava", "--info"))

        then:
        if (gradleVersionNumber == GRADLE_7VERSION) {
            output.contains("No compatible toolchains found for request specification: {languageVersion=15, " +
                    "vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false).")
        } else {
            output.contains("No matching toolchains found for requested specification: {languageVersion=15, " +
                    "vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false).")
        }


        where:
        gradleVersionNumber << [ GRADLE_7VERSION, GRADLE_8VERSION ]
    }

    def '#gradleVersionNumber: gradlew file is correctly generated'() {
        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasSkipped(':wrapperJdkPatcher')
        List<String> initialRows = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        when:
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        def outputWithJdkEnabled = runTasksSuccessfully('wrapper')

        then:
        !outputWithJdkEnabled.wasSkipped(':wrapperJdkPatcher')
        outputWithJdkEnabled.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        List<String> rowsAfterPatching = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        List<String> gradlewPatchRows = Files.readAllLines(Path.of('src/main/resources/gradlew-patch.sh'))
        rowsAfterPatching.removeAll(initialRows)
        rowsAfterPatching.removeAll(gradlewPatchRows)
        rowsAfterPatching.size() == 0

        where:
        gradleVersionNumber << [ GRADLE_7VERSION, GRADLE_8VERSION ]
    }

    def 'no gradleWrapper patch if gradle.jdk.setup.enabled == false'() {
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasSkipped(':wrapperJdkPatcher')
        !output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
    }

    def 'fails if the JDK setup files are missing' () {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        directory('gradle')
        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh"));
        directory('gradle/jdks')
        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-all-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                projectDir.toPath().resolve("gradle/jdks/gradle-jdks-setup.jar"));

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        !output.wasSkipped(':wrapperJdkPatcher')
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")

        when:
        String wrapperResult1 = upgradeGradleWrapper()

        then:
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        wrapperResult1.contains("not found, aborting Gradle JDK setup")

        where:
        gradleVersionNumber << [ GRADLE_7VERSION, GRADLE_8VERSION ]
    }

    void populateGradleFiles(String gradleJdkVersion) {
        populateGradleFiles(gradleJdkVersion, Set.of(gradleJdkVersion))
    }

    void populateGradleFiles(String gradleJdkVersion, Set<String> allJdkVersions) {
        assertThat(allJdkVersions).contains(gradleJdkVersion).as("the list of custom toolchains should also include the gradleJdkVersions")

        String gradleJdkMajorVersion = Iterables.get(Splitter.on('.').split(gradleJdkVersion), 0);
        file('gradle/gradle-jdk-major-version') << gradleJdkMajorVersion + "\n"
        directory('gradle/jdks')

        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-all-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                projectDir.toPath().resolve("gradle/jdks/gradle-jdks-setup.jar"))

        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh"))

        allJdkVersions.forEach(jdkVersion -> addJdk(jdkVersion))

        directory('gradle/certs')
        file('gradle/certs/AmazonRootCA1Test.serial-number') << AMAZON_ROOT_CA_1_SERIAL + "\n"
        directory('gradle/certs')
        file('gradle/certs/Palantir3rdGenRootCa.serial-number') << PALANTIR_3RD_GEN_SERIAL + "\n"
    }

    void addJdk(String jdkVersion) {
        Os os = CurrentOs.get()
        Arch arch = CurrentArch.get()
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(jdkVersion), 0);
        directory(String.format('gradle/jdks/%s/%s/%s', jdkMajorVersion, os, arch))

        JdkPath jdkPath = CORRETTO_JDK_DISTRIBUTION.path(
                JdkRelease.builder().version(jdkVersion).os(os).arch(arch).build());
        String correttoDistributionUrl = Optional.ofNullable(System.getenv("CORRETTO_DISTRIBUTION_URL"))
                .orElseGet(CORRETTO_JDK_DISTRIBUTION::defaultBaseUrl);
        String downloadUrl = String.format(
                String.format("%s/%s.%s\n", correttoDistributionUrl, jdkPath.filename(), jdkPath.extension()))
        String localFilename = getLocalFilename(jdkVersion);
        file(String.format('gradle/jdks/%s/%s/%s/download-url', jdkMajorVersion, os, arch)) << downloadUrl
        file(String.format('gradle/jdks/%s/%s/%s/local-path', jdkMajorVersion, os, arch)) <<  localFilename
    }

    String getLocalFilename(String jdkVersion) {
       return  String.format("amazon-corretto-%s-jdkPluginIntegrationTest\n", jdkVersion);
    }

    String upgradeGradleWrapper() {
        return runGradlewCommand(List.of("./gradlew", "getGradleJavaHomeProp", "wrapper", "--gradle-version", "8.4", "-V", "--stacktrace"))
    }

    String runGradlewCommand(List<String> gradlewCommand) {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(gradlewCommand)
                .directory(projectDir).redirectErrorStream(true)
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir.toAbsolutePath().toString())
        Process process = processBuilder.start()
        process.waitFor()
        return CommandRunner.readAllInput(process.getInputStream())
    }

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    private static void assertBytecodeVersion(File file, int expectedMajorBytecodeVersion,
                                              int expectedMinorBytecodeVersion) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            int minorBytecodeVersion = dis.readUnsignedShort()
            int majorBytecodeVersion = dis.readUnsignedShort()

            assert majorBytecodeVersion == expectedMajorBytecodeVersion
            assert minorBytecodeVersion == expectedMinorBytecodeVersion
        }
    }
}
