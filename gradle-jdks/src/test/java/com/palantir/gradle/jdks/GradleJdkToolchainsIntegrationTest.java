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

import static com.palantir.gradle.jdks.JdkDirectoriesAssert.assertThatJdkDirectories;
import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.jdks.TestResources.Jdk;
import com.palantir.gradle.jdks.setup.JdkSetupFailureException;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksDirectories;
import com.palantir.gradle.jdks.testing.WithJdkAutomanagement;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import com.palantir.platform.OperatingSystem;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
@WithJdkAutomanagement
class GradleJdkToolchainsIntegrationTest {

    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int JAVA_21_BYTECODE = 65;
    private static final int JAVA_23_BYTECODE = 67;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE;

    private static final String DAEMON_MAJOR_VERSION_17 = "17";

    private static final String JAVA_CODE = """
        public class Main {
            public static void main(String[] args) {
                String javaHome = System.getProperty("java.home");
                System.out.println("Java home: " + javaHome);
            }
        }
        """;
    private static final String JAVA_17_PREVIEW_CODE = """
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

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().append("""
            jdks {
                %s
                daemonTarget = '%s'
            }
            """, TestResources.HARDCODED_JDKS.toJdksExtension(), DAEMON_MAJOR_VERSION_17);

        rootProject.buildGradle().plugins().add("application");
        rootProject.buildGradle().append("""
            application {
                mainClass = 'Main'
            }

            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
            """);
    }

    @Test
    void java_toolchains_correctly_set_up(GradleInvoker gradle, RootProject rootProject) {
        rootProject.mainSourceSet().java().writeClass(JAVA_CODE);

        rootProject.buildGradle().append("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            """);

        InvocationResult toolchainsResult = gradle.withArgs("javaToolchains").buildsSuccessfully();
        assertThat(toolchainsResult)
                .output()
                .as("the only discovered jdk versions are coming from gradle.properties")
                .contains("Auto-detection:     Disabled")
                .contains("Auto-download:      Disabled");
        assertThat(TestResources.getDiscoveredLocations(toolchainsResult.output()))
                .allMatch(path -> TestResources.HARDCODED_JDKS.jdks().stream()
                        .map(Jdk::toFilePath)
                        .anyMatch(jdkPath -> jdkPath.startsWith(path)));

        assertThat(TestResources.getDetectedBy(toolchainsResult.output()))
                .as("detected by pattern contains installations.paths or Current JVM")
                .allMatch(a -> a.matches("Gradle property 'org\\.gradle\\.java\\.installations\\.paths'|Current JVM"));

        InvocationResult gradleHomeResult = gradle.withArgs("printGradleHome").buildsSuccessfully();

        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = rootProject
                .file(String.format("gradle/jdks/%s/%s/%s/local-path", DAEMON_MAJOR_VERSION_17, os, arch))
                .text()
                .trim();
        Path daemonJvm = GradleJdksDirectories.getToolchainInstallationDir()
                .resolve(daemonJdkFileName)
                .toAbsolutePath();
        assertThat(gradleHomeResult)
                .output()
                .as("java home is set to the daemon jdk configured version")
                .contains("java.home: " + daemonJvm);

        gradle.withArgs("compileJava").buildsSuccessfully();
        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertThat(readBytecodeVersion(compiledClass))
                .as("the project is compiled with the configured toolchain (17)")
                .isEqualTo(new BytecodeVersion(0, JAVA_17_BYTECODE));

        InvocationResult runResult = gradle.withArgs("run").buildsSuccessfully();
        String compileJdkFileName = rootProject
                .file(String.format("gradle/jdks/17/%s/%s/local-path", os, arch))
                .text()
                .trim();
        Path compileJvm = GradleJdksDirectories.getToolchainInstallationDir()
                .resolve(compileJdkFileName)
                .toAbsolutePath();
        assertThat(runResult)
                .output()
                .as("the application is run with the configured toolchain (17)")
                .contains("Java home: " + compileJvm);
    }

    @Test
    void java_toolchains_correctly_set_up_with_baseline_java(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }
            """);

        SubProject subprojectLib21 = rootProject.subproject("subproject-lib-21");
        subprojectLib21.buildGradle().plugins().add("java-library");
        subprojectLib21.buildGradle().append("""
            javaVersion {
                target = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(JAVA_CODE);

        SubProject subprojectLib11 = rootProject.subproject("subproject-lib-11");
        subprojectLib11.buildGradle().plugins().add("java-library");
        subprojectLib11.buildGradle().append("""
            javaVersion {
                library()
            }
            """);
        subprojectLib11.mainSourceSet().java().writeClass(JAVA_CODE);

        InvocationResult gradleHomeResult = gradle.withArgs("printGradleHome").buildsSuccessfully();
        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = rootProject
                .file(String.format("gradle/jdks/%s/%s/%s/local-path", DAEMON_MAJOR_VERSION_17, os, arch))
                .text()
                .trim();
        Path daemonJvm = GradleJdksDirectories.getToolchainInstallationDir()
                .resolve(daemonJdkFileName)
                .toAbsolutePath();
        assertThat(gradleHomeResult)
                .output()
                .as("java home is set to the daemon jdk configured version")
                .contains("java.home: " + daemonJvm);

        assertThatJdkDirectories(rootProject)
                .as("generates directories for all jdk versions")
                .containsExactJdks(11, 17, 21);

        gradle.withArgs("compileJava").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertThat(readBytecodeVersion(compiledClass))
                .as("the main project is compiled with distributionTarget version")
                .isEqualTo(new BytecodeVersion(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE));

        File subproject11Class = subprojectLib11
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertThat(readBytecodeVersion(subproject11Class))
                .as("the library is compiled with libraryTarget version")
                .isEqualTo(new BytecodeVersion(0, JAVA_11_BYTECODE));

        File subproject21Class = subprojectLib21
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertThat(readBytecodeVersion(subproject21Class))
                .as("the project is compiled with the overridden target version")
                .isEqualTo(new BytecodeVersion(0, JAVA_21_BYTECODE));
    }

    @Test
    void graal_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '23'
            }

            jdks {
                %s
            }
            """, TestResources.GRAALVM_3.toJdkExtension());

        gradle.withArgs("compileJava").buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("generates directories for all used jdk versions")
                .containsExactJdks(17, 23);

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertThat(readBytecodeVersion(compiledClass))
                .as("the main project is compiled with distributionTarget version")
                .isEqualTo(new BytecodeVersion(0, JAVA_23_BYTECODE));
    }

    @Test
    void only_generates_daemon_jdk(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                daemonJdkOnly()
            }
            """);

        gradle.withArgs().buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("only gradle daemon jdk is generated")
                .containsExactJdks(17);
    }

    @Test
    void can_bump_java_major_version_when_baseline_java_is_applied(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
            }
            """);

        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("generates directories for jdk version == 11, 17")
                .containsExactJdks(11, 17);

        gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21")
                .buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("generates directories for jdk versions == 11, 17, 21")
                .containsExactJdks(11, 17, 21);

        InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();
        assertThat(failingCheck)
                .output()
                .as("the check will fail because we have too many jdk files")
                .contains("Unexpected Java versions configured: [21]");

        gradle.withArgs("setupJdks", "compileJava").buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("the extra directory was deleted")
                .containsExactJdks(11, 17);

        gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("generates directories for all jdk versions")
                .containsExactJdks(11, 17, 21);
    }

    @Test
    void only_jdk_versions_to_use_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            jdks {
                jdkMajorVersionsToUse = ["17", "21"]
            }
            """);

        gradle.withArgs("setupJdks").buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("only jdkVersionsToUse files are generated")
                .containsExactJdks(17, 21);
    }

    @Test
    void only_required_java_versions_are_configured(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '17'
            }
            """);

        SubProject subprojectLib21 = rootProject.subproject("subproject-lib-21");
        subprojectLib21.buildGradle().plugins().add("java-library");
        subprojectLib21.buildGradle().append("""
            javaVersion {
                target = 17
                runtime = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(JAVA_CODE);

        gradle.withArgs().buildsSuccessfully();
        assertThatJdkDirectories(rootProject)
                .as("generates directories for all jdk versions")
                .containsExactJdks(17, 21);
    }

    @Test
    void fails_if_the_jdk_version_is_not_configured(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 15
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package helloworld;

            public class HelloWorld {
                public static void main(String[] args) throws Exception {
                    System.out.println("Hello Integration Test");
                }
            }
            """);

        assertThatThrownBy(() -> gradle.withArgs("compileJava").buildsWithFailure())
                .isInstanceOf(JdkSetupFailureException.class)
                .hasMessageContaining("Cannot find a Java installation on your machine")
                .hasMessageContaining("languageVersion=15")
                .hasMessageContaining("Toolchain auto-provisioning is not enabled")
                .hasMessageContaining(
                        "Gradle JDK Auto-management is enabled but the java versions=[15] are not configured.");
    }

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    private static BytecodeVersion readBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException(String.format("File %s does not appear to be java bytecode", file));
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return new BytecodeVersion(minorBytecodeVersion, majorBytecodeVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to read bytecode version from %s", file), e);
        }
    }

    record BytecodeVersion(int minor, int major) {}
}
