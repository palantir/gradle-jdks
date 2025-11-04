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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.google.common.base.Throwables;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import com.palantir.platform.OperatingSystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@GradlePluginTests
class GradleJdkToolchainsIntegrationTest extends GradleJdkIntegrationSpec {

    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int JAVA_21_BYTECODE = 65;
    private static final int JAVA_23_BYTECODE = 67;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;

    @TempDir
    Path workingDir;

    @Test
    void java_toolchains_correctly_set_up(GradleInvoker gradle, RootProject project) throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.mainSourceSet().java().writeClass(getMainJavaCode());

        project.buildGradle()
                .append(
                        """
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

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        // the only discovered jdk versions are coming from gradle.properties
        assertThat(result).output().contains("Auto-detection:     Disabled");
        assertThat(result).output().contains("Auto-download:      Disabled");
        assertThat(result).output().contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_11_VERSION);
        assertThat(result).output().contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_17_VERSION);
        assertThat(result).output().contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_21_VERSION);
        Matcher matcher = Pattern.compile("Detected by:       (.*)").matcher(result.output());
        while (matcher.find()) {
            String detectedByPattern = matcher.group(1);
            org.assertj.core.api.Assertions.assertThat(detectedByPattern)
                    .contains("org.gradle.java.installations.paths");
        }

        // running printGradleHome task
        String gradleHomeOutput = runGradlewTasksSuccessfully(project.path().toFile(), "printGradleHome");

        // java home is set to out jdk 11 configured version
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = Files.readString(project.path()
                        .resolve("gradle/jdks/" + GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17 + "/" + os + "/" + arch
                                + "/local-path"))
                .trim();
        Path daemonJvm =
                workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath();
        org.assertj.core.api.Assertions.assertThat(gradleHomeOutput).contains("java.home: " + daemonJvm);

        // running compileJava task
        runGradlewTasksSuccessfully(project.path().toFile(), "compileJava");

        // the project is compiled with the configured toolchain (17)
        File compiledClass = new File(project.path().toFile(), "build/classes/java/main/Main.class");
        org.assertj.core.api.Assertions.assertThat(readBytecodeVersion(compiledClass))
                .isEqualTo(Pair.of(0, JAVA_17_BYTECODE));

        // running run task
        String runOutput = runGradlewTasksSuccessfully(project.path().toFile(), "run");

        // the application is run with the configured toolchain (17)
        String compileJdkFileName = Files.readString(
                        project.path().resolve("gradle/jdks/17/" + os + "/" + arch + "/local-path"))
                .trim();
        Path compileJvm =
                workingDir().resolve("gradle-jdks").resolve(compileJdkFileName).toAbsolutePath();
        org.assertj.core.api.Assertions.assertThat(runOutput).contains("Java home: " + compileJvm);
    }

    @Test
    void java_toolchains_correctly_set_up_with_baseline_java(
            GradleInvoker gradle, RootProject project, SubProject subprojectLib21, SubProject subprojectLib11)
            throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(java17PreviewCode());

        project.buildGradle()
                .append(
                        """
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

        subprojectLib21
                .buildGradle()
                .append(
                        """
            plugins {
                id 'java-library'
            }
            javaVersion {
               target = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(getMainJavaCode());

        subprojectLib11
                .buildGradle()
                .append(
                        """
            plugins {
                id 'java-library'
            }
            javaVersion {
                library()
            }
            """);
        subprojectLib11.mainSourceSet().java().writeClass(getMainJavaCode());

        gradle.withArgs("wrapper").buildsSuccessfully();

        // running printGradleHome task
        String gradleHomeOutput = runGradlewTasksSuccessfully(project.path().toFile(), "printGradleHome");

        // java home is set to out jdk 11 configured version
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = Files.readString(project.path()
                        .resolve("gradle/jdks/" + GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17 + "/" + os + "/" + arch
                                + "/local-path"))
                .trim();
        Path daemonJvm =
                workingDir().resolve("gradle-jdks").resolve(daemonJdkFileName).toAbsolutePath();
        org.assertj.core.api.Assertions.assertThat(gradleHomeOutput).contains("java.home: " + daemonJvm);

        // generates directories for all jdk versions
        Set<String> jdkVersions = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions).isEqualTo(Set.of("11", "17", "21"));

        // compiling projects
        String output = runGradlewTasksSuccessfully(project.path().toFile(), "compileJava", "--info");

        // the main project is compiled with `distributionTarget` version
        File compiledClass = new File(project.path().toFile(), "build/classes/java/main/Main.class");
        org.assertj.core.api.Assertions.assertThat(readBytecodeVersion(compiledClass))
                .isEqualTo(Pair.of(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE));

        // the library is compiled with `libraryTarget` version
        File subproject11Class = new File(subprojectLib11.path().toFile(), "build/classes/java/main/Main.class");
        org.assertj.core.api.Assertions.assertThat(readBytecodeVersion(subproject11Class))
                .isEqualTo(Pair.of(0, JAVA_11_BYTECODE));

        // the project is compiled with the overridden `target` version
        File subproject21Class = new File(subprojectLib21.path().toFile(), "build/classes/java/main/Main.class");
        org.assertj.core.api.Assertions.assertThat(readBytecodeVersion(subproject21Class))
                .isEqualTo(Pair.of(0, JAVA_21_BYTECODE));
    }

    @Test
    void graal_jdks_are_generated(GradleInvoker gradle, RootProject project) throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(java17PreviewCode());

        project.buildGradle()
                .append(
                        """
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

        // running printGradleHome task
        gradle.withArgs("wrapper").buildsSuccessfully();

        // generates directories for all used jdk versions
        // only the daemonTarget and graal JDK versions are used
        Set<String> jdkVersions = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions)
                .isEqualTo(Set.of(GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17, "23"));

        // compiling projects
        String output = runGradlewTasksSuccessfully(project.path().toFile(), "compileJava", "--info");

        // the main project is compiled with `distributionTarget` version
        File compiledClass = new File(project.path().toFile(), "build/classes/java/main/Main.class");
        org.assertj.core.api.Assertions.assertThat(readBytecodeVersion(compiledClass))
                .isEqualTo(Pair.of(0, JAVA_23_BYTECODE));
    }

    @Test
    void only_generates_daemon_jdk(GradleInvoker gradle, RootProject project) throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.buildGradle()
                .append("""
            jdks {
                daemonJdkOnly()
            }
            """);

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(java17PreviewCode());

        gradle.withArgs("wrapper").buildsSuccessfully();

        // only gradle daemon jdk is generated
        boolean allMatch = Files.list(project.path().resolve("gradle/jdks"))
                .allMatch(
                        it -> it.endsWith(String.format("gradle/jdks/%s", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17)));
        org.assertj.core.api.Assertions.assertThat(allMatch).isTrue();
    }

    @Test
    void can_bump_java_major_version_when_baseline_java_is_applied(GradleInvoker gradle, RootProject project)
            throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.buildGradle()
                .append(
                        """
            javaVersions {
                libraryTarget = '11'
            }
            """);

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(getMainJavaCode());
        gradle.withArgs("wrapper").buildsSuccessfully();

        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        // generates directories for jdk version == 11, 17
        Set<String> jdkVersions1 = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions1).isEqualTo(Set.of("11", "17"));

        gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21")
                .buildsSuccessfully();

        // generates directories for jdk versions == 11, 17, 21
        Set<String> jdkVersions2 = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions2).isEqualTo(Set.of("11", "17", "21"));

        InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();

        // the check will fail because we have too many jdk files
        org.assertj.core.api.Assertions.assertThat(Throwables.getRootCause(new RuntimeException(failingCheck.output()))
                        .getMessage())
                .contains("Unexpected Java versions configured: [21]");

        InvocationResult output = gradle.withArgs("setupJdks", "compileJava").buildsSuccessfully();

        // the extra directory was deleted
        Set<String> jdkVersions3 = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions3)
                .isEqualTo(Set.of("11", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17));

        gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();

        // generates directories for all jdk versions
        Set<String> jdkVersions4 = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions4).isEqualTo(Set.of("11", "17", "21"));
    }

    @Test
    void only_jdk_versions_to_use_jdks_are_generated(GradleInvoker gradle, RootProject project) throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(java17PreviewCode());
        gradle.withArgs("wrapper").buildsSuccessfully();

        project.buildGradle()
                .append(
                        """
            jdks {
                jdkMajorVersionsToUse = ["17", "21"]
            }
            """);

        gradle.withArgs("setupJdks").buildsSuccessfully();

        // only jdkVersionsToUse files are generated
        Set<String> jdkVersions = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions).isEqualTo(Set.of("17", "21"));
    }

    @Test
    void only_required_java_versions_are_configured(GradleInvoker gradle, RootProject project, SubProject subproject)
            throws IOException {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());
        GradleJdkTestUtils.applyApplicationPlugin(project.buildGradle().path());

        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");
        project.mainSourceSet().java().writeClass(java17PreviewCode());

        project.buildGradle()
                .append(
                        """
            javaVersions {
                libraryTarget = '17'
            }
            """);

        subproject
                .buildGradle()
                .append(
                        """
            plugins {
                id 'java-library'
            }
            javaVersion {
               target = 17
               runtime = 21
            }
            """);
        subproject.mainSourceSet().java().writeClass(getMainJavaCode());

        gradle.withArgs("wrapper").buildsSuccessfully();

        // generates directories for all jdk versions
        Set<String> jdkVersions = Files.list(project.path().resolve("gradle/jdks"))
                .map(it -> it.getFileName().toString())
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(jdkVersions).isEqualTo(Set.of("17", "21"));
    }

    @Test
    void fails_if_the_jdk_version_is_not_configured_gradle_7_6_4(GradleInvoker gradle, RootProject project) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());

        project.buildGradle()
                .append(
                        """
            javaVersions {
                libraryTarget = 15
            }
            """);
        project.mainSourceSet()
                .java()
                .writeClass(
                        """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """);
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // generate the ./gradlew task
        gradle.withArgs("wrapper").buildsSuccessfully();

        String result = runGradlewTasksWithFailure(project.path().toFile(), "compileJava");

        List<String> expectedErrorLines =
                List.of("No compatible toolchains found for request specification: {languageVersion=15, vendor=any,"
                        + " implementation=vendor-specific} (auto-detect false, auto-download false).");
        expectedErrorLines.forEach(expectedErrorLine ->
                org.assertj.core.api.Assertions.assertThat(result).contains(expectedErrorLine));
        org.assertj.core.api.Assertions.assertThat(result)
                .doesNotContain("If you are trying to manually change the JDK versions used");
    }

    @Test
    void fails_if_the_jdk_version_is_not_configured_gradle_8_5(GradleInvoker gradle, RootProject project) {
        GradleJdkTestUtils.setupJdksHardcodedVersions(
                project.settingsGradle().path(), project.buildGradle().path());
        GradleJdkTestUtils.applyBaselineJavaVersions(project.buildGradle().path());

        project.buildGradle()
                .append(
                        """
            javaVersions {
                libraryTarget = 15
            }
            """);
        project.mainSourceSet()
                .java()
                .writeClass(
                        """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """);
        project.gradlePropertiesFile().append("palantir.jdk.setup.enabled=true");

        // generate the ./gradlew task
        gradle.withArgs("wrapper").buildsSuccessfully();

        String result = runGradlewTasksWithFailure(project.path().toFile(), "compileJava");

        List<String> expectedErrorLines = List.of(
                "No matching toolchains found for requested specification: {languageVersion=15, vendor=any,"
                        + " implementation=vendor-specific}",
                "No locally installed toolchains match and toolchain auto-provisioning is not enabled.");
        expectedErrorLines.forEach(expectedErrorLine ->
                org.assertj.core.api.Assertions.assertThat(result).contains(expectedErrorLine));
        org.assertj.core.api.Assertions.assertThat(result)
                .contains("If you are trying to manually change the JDK versions used");
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

    @Override
    Path workingDir() {
        return workingDir;
    }
}
