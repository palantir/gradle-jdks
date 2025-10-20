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

package com.palantir.gradle.jdks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Throwables;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.platform.OperatingSystem;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class GradleJdkToolchainsIntegrationTest extends GradleJdkIntegrationTest {

    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int JAVA_21_BYTECODE = 65;
    private static final int JAVA_23_BYTECODE = 67;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;

    @TempDir
    Path workingDir;

    @Override
    Path workingDir() {
        return workingDir;
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void javaToolchainsCorrectlySetup(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyApplicationPlugin();

        createFile("src/main/java/Main.java").overwrite(getMainJavaCode());

        rootProject.buildGradle().append("""
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
        """);
        
        gradle.withArgs("wrapper").buildsSuccessfully();

        // When running setupJdks
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        // Then the only discovered jdk versions are coming from gradle.properties
        assertTrue(result.output().contains("Auto-detection:     Disabled"));
        assertTrue(result.output().contains("Auto-download:      Disabled"));
        assertTrue(result.output().contains("JDK " + SIMPLIFIED_JDK_11_VERSION));
        assertTrue(result.output().contains("JDK " + SIMPLIFIED_JDK_17_VERSION));
        assertTrue(result.output().contains("JDK " + SIMPLIFIED_JDK_21_VERSION));
        
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Detected by:       (.*)").matcher(result.output());
        while (matcher.find()) {
            String detectedByPattern = matcher.group(1);
            assertTrue(detectedByPattern.contains("org.gradle.java.installations.paths"));
        }

        // When running printGradleHome task
        String gradleHomeOutput = runGradlewTasksSuccessfully("printGradleHome");

        // Then java home is set to our jdk 11 configured version
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = Files.readString(rootProject.path().resolve(
                String.format("gradle/jdks/%s/%s/%s/local-path", 
                        DAEMON_MAJOR_VERSION_17, os, arch))).trim();
        Path daemonJvm = workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath();
        
        assertTrue(gradleHomeOutput.contains("java.home: " + daemonJvm));

        // When running compileJava task
        runGradlewTasksSuccessfully("compileJava");

        // Then the project is compiled with the configured toolchain (17)
        File compiledClass = new File(rootProject.path().toFile(), "build/classes/java/main/Main.class");
        assertEquals(Pair.of(0, JAVA_17_BYTECODE), readBytecodeVersion(compiledClass));

        // When running run task
        String runOutput = runGradlewTasksSuccessfully("run");

        // Then the application is run with the configured toolchain (17)
        String compileJdkFileName = Files.readString(rootProject.path().resolve(
                String.format("gradle/jdks/17/%s/%s/local-path", os, arch))).trim();
        Path compileJvm = workingDir().resolve("gradle-jdks").resolve(compileJdkFileName).toAbsolutePath();
        
        assertTrue(runOutput.contains("Java home: " + compileJvm));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void javaToolchainsCorrectlySetupWithBaselineJava(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();
        applyApplicationPlugin();

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(java17PreviewCode());

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
            
            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
        """);

        // Create subprojects
        rootProject.subproject("subproject-lib-21").buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
               target = 21
            }
        """);
        rootProject.subproject("subproject-lib-21").mainSourceSet().java().writeClass(getMainJavaCode());

        rootProject.subproject("subproject-lib-11").buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
                library()
            }
        """);
        rootProject.subproject("subproject-lib-11").mainSourceSet().java().writeClass(getMainJavaCode());
        
        gradle.withArgs("wrapper").buildsSuccessfully();

        // When running printGradleHome task
        String gradleHomeOutput = runGradlewTasksSuccessfully("printGradleHome");

        // Then java home is set to our jdk 11 configured version
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = Files.readString(rootProject.path().resolve(
                String.format("gradle/jdks/%s/%s/%s/local-path", 
                        DAEMON_MAJOR_VERSION_17, os, arch))).trim();
        Path daemonJvm = workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath();
        
        assertTrue(gradleHomeOutput.contains("java.home: " + daemonJvm));

        // Then generates directories for all jdk versions
        Set<String> jdkVersions;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("11", "17", "21"), jdkVersions);

        // When compiling projects
        InvocationResult output = gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        // Then the main project is compiled with `distributionTarget` version
        File compiledClass = new File(rootProject.path().toFile(), "build/classes/java/main/Main.class");
        assertEquals(Pair.of(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE), readBytecodeVersion(compiledClass));

        // And the library is compiled with `libraryTarget` version
        File subproject11Class = new File(rootProject.path().resolve("subproject-lib-11").toFile(), 
                "build/classes/java/main/Main.class");
        assertEquals(Pair.of(0, JAVA_11_BYTECODE), readBytecodeVersion(subproject11Class));

        // And the project is compiled with the overridden `target` version
        File subproject21Class = new File(rootProject.path().resolve("subproject-lib-21").toFile(), 
                "build/classes/java/main/Main.class");
        assertEquals(Pair.of(0, JAVA_21_BYTECODE), readBytecodeVersion(subproject21Class));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void graalJdksAreGenerated(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();
        applyApplicationPlugin();

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(java17PreviewCode());

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '23'
            }
            
            jdks {
                jdk(23) {
                    distribution = 'graalvm-ce'
                    jdkVersion = '23.0.1'
                }
            }
        """);

        // When running printGradleHome task
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Then generates directories for all used jdk versions
        Set<String> jdkVersions;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of(DAEMON_MAJOR_VERSION_17, "23"), jdkVersions);

        // When compiling projects
        InvocationResult output = gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        // Then the main project is compiled with `distributionTarget` version
        File compiledClass = new File(rootProject.path().toFile(), "build/classes/java/main/Main.class");
        assertEquals(Pair.of(0, JAVA_23_BYTECODE), readBytecodeVersion(compiledClass));
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void onlyGeneratesDaemonJdk(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();
        applyApplicationPlugin();

        rootProject.buildGradle().append("""
            jdks {
                daemonJdkOnly()
            }
        """);

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(java17PreviewCode());

        // When running tasks
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Then only gradle daemon jdk is generated
        boolean allMatch;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            allMatch = stream
                    .allMatch(p -> p.getFileName().toString().equals(DAEMON_MAJOR_VERSION_17));
        }
                
        assertTrue(allMatch);
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void canBumpJavaMajorVersionWhenBaselineJavaIsApplied(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();
        applyApplicationPlugin();

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
            }
        """);

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(getMainJavaCode());
        
        gradle.withArgs("wrapper").buildsSuccessfully();

        // When running generateGradleJdkConfigs
        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        // Then generates directories for jdk version == 11, 17
        Set<String> jdkVersions1;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions1 = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("11", "17"), jdkVersions1);

        // When including version 21
        gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21").buildsSuccessfully();

        // Then generates directories for jdk versions == 11, 17, 21
        Set<String> jdkVersions2;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions2 = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("11", "17", "21"), jdkVersions2);

        // When running check (expecting failure)
        InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();

        // Then the check will fail because we have too many jdk files
        assertTrue(Throwables.getRootCause(new RuntimeException(failingCheck.output()))
                .getMessage().contains("Unexpected Java versions configured: [21]"));

        // When running setupJdks and compileJava
        InvocationResult output = gradle.withArgs("setupJdks", "compileJava").buildsSuccessfully();

        // Then the extra directory was deleted
        Set<String> jdkVersions3;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions3 = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("11", DAEMON_MAJOR_VERSION_17), jdkVersions3);

        // When including all JDKs
        gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();

        // Then generates directories for all jdk versions
        Set<String> jdkVersions4;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions4 = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("11", "17", "21"), jdkVersions4);
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void onlyJdkVersionsToUseJdksAreGenerated(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyApplicationPlugin();

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(java17PreviewCode());
        
        gradle.withArgs("wrapper").buildsSuccessfully();

        // When configuring specific JDK versions
        rootProject.buildGradle().append("""
            jdks {
                jdkMajorVersionsToUse = ["17", "21"]
            }
        """);

        gradle.withArgs("setupJdks").buildsSuccessfully();

        // Then only jdkVersionsToUse files are generated
        Set<String> jdkVersions;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("17", "21"), jdkVersions);
    }

    @ParameterizedTest
    @MethodSource("GRADLE_TEST_VERSIONS")
    void onlyRequiredJavaVersionsAreConfigured(String gradleVersionNumber, GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();
        applyApplicationPlugin();

        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        createFile("src/main/java/Main.java").overwrite(java17PreviewCode());

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '17'
            }
        """);

        // Create subproject with specific Java versions
        rootProject.subproject("subproject-lib-21").buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
               target = 17
               runtime = 21
            }
        """);
        rootProject.subproject("subproject-lib-21").mainSourceSet().java().writeClass(getMainJavaCode());

        // When running wrapper
        gradle.withArgs("wrapper").buildsSuccessfully();

        // Then generates directories for all jdk versions
        Set<String> jdkVersions;
        try (Stream<Path> stream = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            jdkVersions = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
                
        assertEquals(Set.of("17", "21"), jdkVersions);
    }

    @ParameterizedTest
    @CsvSource({
            "7.6.4, 'No compatible toolchains found for request specification: {languageVersion=15, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false)', false",
            "8.5, 'No matching toolchains found for requested specification: {languageVersion=15, vendor=any, implementation=vendor-specific}', true"
    })
    void failsIfTheJdkVersionIsNotConfigured(String gradleVersionNumber, String expectedErrorLine, boolean shouldLogExplanation, 
            GradleInvoker gradle) throws IOException {
        setupJdksHardcodedVersions();
        applyBaselineJavaVersions();

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 15
            }
        """);
        
        writeHelloWorld(rootProject.path().toFile());
        createFile("gradle.properties").overwrite("palantir.jdk.setup.enabled=true");
        
        // Generate the ./gradlew task
        gradle.withArgs("wrapper").buildsSuccessfully();

        // When running compileJava (expecting failure)
        String result = runGradlewTasksWithFailure("compileJava");

        // Then verify error messages
        assertTrue(result.contains(expectedErrorLine));
        
        if (shouldLogExplanation) {
            assertTrue(result.contains("If you are trying to manually change the JDK versions used"));
        }
    }

    private String java17PreviewCode() {
        return """
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
            """;
    }

    private String getMainJavaCode() {
        return """
            public class Main {
                public static void main(String[] args) {
                    String javaHome = System.getProperty("java.home");
                    System.out.println("Java home: " + javaHome);
                }
            }
            """;
    }
    
    private void writeHelloWorld(File projectDir) {
        try {
            File srcDir = new File(projectDir, "src/main/java");
            srcDir.mkdirs();
            Files.writeString(new File(srcDir, "HelloWorld.java").toPath(), """
                public class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                """);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}