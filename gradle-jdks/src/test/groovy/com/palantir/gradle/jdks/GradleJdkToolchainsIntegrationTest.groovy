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

import com.palantir.gradle.jdks.setup.common.CurrentArch
import com.palantir.gradle.jdks.setup.common.CurrentOs
import nebula.test.functional.ExecutionResult
import org.apache.commons.lang3.tuple.Pair
import spock.lang.TempDir

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream

class GradleJdkToolchainsIntegrationTest extends GradleJdkIntegrationTest {

    private static final int JAVA_11_BYTECODE = 55
    private static final int JAVA_17_BYTECODE = 61
    private static final int JAVA_21_BYTECODE = 65
    private static final int ENABLE_PREVIEW_BYTECODE = 65535

    @TempDir
    Path workingDir


    def '#gradleVersionNumber: non-existing jdks are installed by the settings plugin'() {
        gradleVersion = gradleVersionNumber
        applyJdksPlugins()

        // language=groovy
        buildFile << """
            jdks {
               jdk(17) {
                  distribution = JDK_17_DISTRO
                  jdkVersion = JDK_17_VERSION
               }
               
                daemonTarget = '17'
            }
        """.replace("JDK_17_DISTRO", quoted(JDK_17.getLeft()))
                .replace("JDK_17_VERSION", quoted(JDK_17.getRight()))
                .stripIndent(true)

        when:
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        runTasksSuccessfully("generateGradleJdkConfigs")

        then: 'only gradle configuration files are generated, no jdks are installed'
        String os = CurrentOs.get().uiName()
        String arch = CurrentArch.get().uiName()
        Path jdk17LocalPath = projectDir.toPath().resolve("gradle/jdks/17/${os}/${arch}/local-path")
        String compileJdkFileName = jdk17LocalPath.text.trim()
        Path installedJdkPath = Path.of(System.getProperty("user.home")).resolve(".gradle/gradle-jdks").resolve(compileJdkFileName).toAbsolutePath()
        !Files.exists(installedJdkPath)

        when: 'trigger a task'
        ExecutionResult executionResult = runTasksSuccessfully("javaToolchains")

        then: 'the jdks are installed by the settings plugin'
        executionResult.standardError.contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed")
        executionResult.standardOutput.contains("Auto-detection:     Disabled")
        executionResult.standardOutput.contains("Auto-download:      Disabled")
        executionResult.standardOutput.contains("JDK ${SIMPLIFIED_JDK_17_VERSION}")
        Files.exists(installedJdkPath)

        when: 'if the jdk configured path is changed'
        jdk17LocalPath.text = "amazon-corretto-another-path\n"
        ExecutionResult resultAfterJdkChange = runTasksSuccessfully("javaToolchains")

        then:
        resultAfterJdkChange.standardError.contains("Gradle JDK setup is enabled (palantir.jdk.setup.enabled is true)" +
                " but some jdks were not installed")
        Path newInstalledJdkPath = Path.of(System.getProperty("user.home")).resolve(".gradle/gradle-jdks").resolve("amazon-corretto-another-path").toAbsolutePath()
        Files.exists(newInstalledJdkPath)

        cleanup:
        Files.walk(installedJdkPath)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)

        Files.walk(newInstalledJdkPath)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)

        where:
        gradleVersionNumber << [GRADLE_7_6_VERSION]
    }

    def '#gradleVersionNumber: javaToolchains correctly set-up'() {
        gradleVersion = gradleVersionNumber
        setupJdksHardcodedVersions()
        applyApplicationPlugin()

        file('src/main/java/Main.java') << getMainJavaCode()

        // language=Groovy
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            
            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
        """.stripIndent(true)
        runTasksSuccessfully("wrapper")

        when:
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        def result = runTasksSuccessfully("setupJdks")

        then: 'the only discovered jdk versions are coming from gradle.properties'
        result.standardOutput.contains("Auto-detection:     Disabled")
        result.standardOutput.contains("Auto-download:      Disabled")
        result.standardOutput.contains("JDK ${SIMPLIFIED_JDK_11_VERSION}")
        result.standardOutput.contains("JDK ${SIMPLIFIED_JDK_17_VERSION}")
        result.standardOutput.contains("JDK ${SIMPLIFIED_JDK_21_VERSION}")
        Matcher matcher = Pattern.compile("Detected by:       (.*)").matcher(result.standardOutput)
        while (matcher.find()) {
            String detectedByPattern = matcher.group(1)
            detectedByPattern.contains('org.gradle.java.installations.paths')
        }

        when: 'running printGradleHome task'
        String gradleHomeOutput = runGradlewTasksSuccessfully("printGradleHome")

        then: 'java home is set to out jdk 11 configured version'
        String os = CurrentOs.get().uiName()
        String arch = CurrentArch.get().uiName()
        String daemonJdkFileName = projectDir.toPath().resolve("gradle/jdks/${DAEMON_MAJOR_VERSION_11}/${os}/${arch}/local-path").text.trim()
        Path daemonJvm = workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath()
        gradleHomeOutput.contains("java.home: ${daemonJvm}")

        when: 'running compileJava task'
        runGradlewTasksSuccessfully("compileJava")

        then: 'the project is compiled with the configured toolchain (17)'
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")
        readBytecodeVersion(compiledClass) == Pair.of(0, JAVA_17_BYTECODE)

        when: 'running run task'
        String runOutput = runGradlewTasksSuccessfully("run")

        then: 'the application is run with the configured toolchain (17)'
        String compileJdkFileName = projectDir.toPath().resolve("gradle/jdks/17/${os}/${arch}/local-path").text.trim()
        Path compileJvm = workingDir().resolve("gradle-jdks").resolve(compileJdkFileName).toAbsolutePath()
        runOutput.contains("Java home: ${compileJvm}")

        where:
        gradleVersionNumber << [GRADLE_7_6_VERSION, GRADLE_7_6_4_VERSION, GRADLE_8_5_VERSION, GRADLE_8_8_VERSION]
    }


    def '#gradleVersionNumber: javaToolchains correctly set-up with baseline-java'() {
        gradleVersion = gradleVersionNumber
        setupJdksHardcodedVersions()
        applyBaselineJavaVersions()
        applyApplicationPlugin()

        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        file('src/main/java/Main.java') << java17PreviewCode

        // language=Groovy
        buildFile << """
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
            
            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
        """.stripIndent(true)

        //language=groovy
        def subprojectLib21 = addSubproject 'subproject-lib-21', '''
            apply plugin: 'java-library'
            javaVersion {
               target = 21
            }
        '''.stripIndent(true)
        writeJavaSourceFile(getMainJavaCode(), subprojectLib21)

        //language=groovy
        def subprojectLib11 = addSubproject 'subproject-lib-11', '''
            apply plugin: 'java-library'
            javaVersion {
                library()
            }
        '''.stripIndent(true)
        writeJavaSourceFile(getMainJavaCode(), subprojectLib11)
        runTasksSuccessfully('wrapper')

        when: 'running printGradleHome task'
        String gradleHomeOutput = runGradlewTasksSuccessfully("printGradleHome")

        then: 'java home is set to out jdk 11 configured version'
        String os = CurrentOs.get().uiName()
        String arch = CurrentArch.get().uiName()
        String daemonJdkFileName = projectDir.toPath().resolve("gradle/jdks/${DAEMON_MAJOR_VERSION_11}/${os}/${arch}/local-path").text.trim()
        Path daemonJvm = workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath()
        gradleHomeOutput.contains("java.home: ${daemonJvm}")

        when: 'compiling projects'
        def output = runGradlewTasksSuccessfully("compileJava", "--info")

        then: 'the main project is compiled with `distributionTarget` version'
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")
        readBytecodeVersion(compiledClass) == Pair.of(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE)

        and: 'the library is compiled with `libraryTarget` version'
        File subproject11Class = new File(subprojectLib11, "build/classes/java/main/Main.class")
        readBytecodeVersion(subproject11Class) == Pair.of(0, JAVA_11_BYTECODE)

        and: 'the project is compiled with the overridden `target` version'
        File subproject21Class = new File(subprojectLib21, "build/classes/java/main/Main.class")
        readBytecodeVersion(subproject21Class) == Pair.of(0, JAVA_21_BYTECODE)

        where:
        gradleVersionNumber << [GRADLE_7_6_4_VERSION, GRADLE_8_5_VERSION]
    }

    def '#gradleVersionNumber: fails if the jdk version is not configured'() {
        setupJdksHardcodedVersions()
        applyBaselineJavaVersions()

        gradleVersion = gradleVersionNumber

        buildFile << '''
            javaVersions {
                distributionTarget = '15'
            }
        '''.stripIndent(true)
        writeHelloWorld(projectDir)
        file('gradle.properties') << 'palantir.jdk.setup.enabled=true'
        // generate the ./gradlew task
        runTasksSuccessfully("wrapper")

        when:
        def result = runGradlewTasksWithFailure("compileJava")

        then:
        expectedErrorLines.forEach { expectedErrorLine -> result.contains(expectedErrorLine) }

        where:
        gradleVersionNumber  | expectedErrorLines
        GRADLE_7_6_4_VERSION | ["No compatible toolchains found for request specification: {languageVersion=15, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false)."]
        GRADLE_8_5_VERSION   | ["No matching toolchains found for requested specification: {languageVersion=15, vendor=any, implementation=vendor-specific}", "No locally installed toolchains match and toolchain auto-provisioning is not enabled."]
    }

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
                String javaHome = System.getProperty("java.home");
                System.out.println("Java home: " + javaHome);
            }
        }
        '''

    def getMainJavaCode() {
        return '''
            public class Main {
                public static void main(String[] args) {
                    String javaHome = System.getProperty("java.home");
                    System.out.println("Java home: " + javaHome);
                }
            }
            '''.stripIndent(true)
    }

    @Override
    Path workingDir() {
        return workingDir
    }
}
