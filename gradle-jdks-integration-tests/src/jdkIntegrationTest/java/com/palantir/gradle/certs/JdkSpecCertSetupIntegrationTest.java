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

package com.palantir.gradle.certs;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.palantir.gradle.jdks.AmazonCorrettoJdkDistribution;
import com.palantir.gradle.jdks.Arch;
import com.palantir.gradle.jdks.CurrentArch;
import com.palantir.gradle.jdks.CurrentOs;
import com.palantir.gradle.jdks.JdkPath;
import com.palantir.gradle.jdks.JdkRelease;
import com.palantir.gradle.jdks.Os;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

public class JdkSpecCertSetupIntegrationTest {

    private static final String SUCCESSFUL_OUTPUT = "Successfully installed JDK distribution, setting JAVA_HOME to";
    private static final String JDK_VERSION = "11.0.21.9.1";
    private static final Arch ARCH = CurrentArch.get();
    private static final String TEST_HASH = "integration-tests";
    private static final AmazonCorrettoJdkDistribution distribution = new AmazonCorrettoJdkDistribution();

    @Test
    public void can_setup_jdk_with_certs_centos() throws IOException, InterruptedException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_GLIBC);
        String expectedDistributionPath =
                String.format("/root/.gradle/gradle-jdks/amazon-corretto-%s-%s", JDK_VERSION, TEST_HASH);
        assertThat(execInDocker("centos:7", "/bin/bash", temporaryGradleDirectory, "testing-script.sh"))
                .contains(SUCCESSFUL_OUTPUT)
                .contains(String.format("Java home is: %s", expectedDistributionPath))
                .contains(String.format("Java path is: %s", expectedDistributionPath))
                .contains(String.format("Java version is: %s", getJavaVersion(JDK_VERSION)))
                .contains("Palantir CA was not imported in the JDK truststore");
    }

    @Test
    public void can_setup_jdk_with_certs_ubuntu() throws IOException, InterruptedException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_GLIBC);
        String expectedDistributionPath =
                String.format("/root/.gradle/gradle-jdks/amazon-corretto-%s-%s", JDK_VERSION, TEST_HASH);
        assertThat(execInDocker("ubuntu:20.04", "/bin/bash", temporaryGradleDirectory, "testing-script.sh"))
                .contains(SUCCESSFUL_OUTPUT)
                .contains(String.format("Java home is: %s", expectedDistributionPath))
                .contains(String.format("Java path is: %s", expectedDistributionPath))
                .contains(String.format("Java version is: %s", getJavaVersion(JDK_VERSION)))
                .contains("Palantir CA was not imported in the JDK truststore");
    }

    @Test
    public void can_setup_jdk_with_certs_alpine() throws IOException, InterruptedException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_MUSL);
        String expectedDistributionPath =
                String.format("/root/.gradle/gradle-jdks/amazon-corretto-%s-%s", JDK_VERSION, TEST_HASH);
        assertThat(execInDocker("alpine:3.16.0", "/bin/sh", temporaryGradleDirectory, "testing-script.sh"))
                .contains(SUCCESSFUL_OUTPUT)
                .contains(String.format("Java home is: %s", expectedDistributionPath))
                .contains(String.format("Java path is: %s", expectedDistributionPath))
                .contains(String.format("Java version is: %s", getJavaVersion(JDK_VERSION)))
                .contains("Palantir CA was not imported in the JDK truststore");
    }

    @Test
    @EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    public void can_setup_locally_from_scratch() throws IOException, InterruptedException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, CurrentOs.get());
        String expectedJavaHomeVersion = String.format(
                "%s/.gradle/gradle-jdks/amazon-corretto-%s-%s", System.getenv("HOME"), JDK_VERSION, TEST_HASH);
        Path expectedJavaHome = Path.of(expectedJavaHomeVersion);
        if (Files.exists(expectedJavaHome)) {
            FileUtils.deleteDirectory(expectedJavaHome.toFile());
        }
        assertThat(runCommandWithZeroExitCode(List.of(
                        "/bin/bash",
                        temporaryGradleDirectory
                                .resolve("gradle-jdk-resolver.sh")
                                .toAbsolutePath()
                                .toString())))
                .contains(String.format(SUCCESSFUL_OUTPUT + " %s", expectedJavaHomeVersion))
                .contains("Successfully imported Palantir CA certificate into the JDK truststore");
        assertThat(runCommandWithZeroExitCode(List.of(
                        "/bin/bash",
                        temporaryGradleDirectory
                                .resolve("gradle-jdk-resolver.sh")
                                .toAbsolutePath()
                                .toString())))
                .contains(String.format("already exists, setting JAVA_HOME to %s", expectedJavaHomeVersion));
        assertThat(Files.exists(expectedJavaHome)).isTrue();
        // TODO(crogoz): maybe have this in a custom directory?
        FileUtils.deleteDirectory(expectedJavaHome.toFile());
    }

    private static Path setupGradleDirectoryStructure(String jdkVersion, Os os) throws IOException {
        /**
         * Each project will contain the following gradle file structure:
         * Note! Make sure the files end in a newline character, otherwise the `read` command in the
         * gradle-jdk-resolver.sh will fail!
         * project-root/
         * ├── gradle/
         * │   ├── wrapper/
         * │   │   ├── gradle-wrapper.jar
         * │   │   ├── gradle-wrapper.properties
         * │   ├── jdks/
         * │   │   ├── <jdkMajorVersion eg.11>/
         * │   │   │   ├── <os eg. linux>/
         * │   │   │   │   ├── <arch eg. aarch64>/
         * │   │   │   │   │   ├── download-url
         * │   │   │   │   │   ├── local-path
         * │   ├── gradle-jdk-major-version
         * │   ├── gradle-jdks-certs.jar
         * ├── subProjects/...
         * ...
         */
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(jdkVersion), 0);
        Path gradleDirectory = Files.createTempDirectory("gradle");
        Path gradleJdkVersion = Files.createFile(gradleDirectory.resolve("gradle-jdk-major-version"));
        writeFileContent(gradleJdkVersion, jdkMajorVersion.toString());
        JdkPath jdkPath = distribution.path(
                JdkRelease.builder().version(jdkVersion).os(os).arch(ARCH).build());
        Path archDirectory = Files.createDirectories(
                gradleDirectory.resolve(String.format("jdks/%s/%s/%s", jdkMajorVersion, os.uiName(), ARCH.uiName())));
        Path downloadUrlPath = Files.createFile(archDirectory.resolve("download-url"));
        writeFileContent(
                downloadUrlPath,
                String.format(String.format(
                        "%s/%s.%s", distribution.defaultBaseUrl(), jdkPath.filename(), jdkPath.extension())));
        Path localPath = Files.createFile(archDirectory.resolve("local-path"));
        writeFileContent(localPath, String.format("amazon-corretto-%s-%s", jdkVersion, TEST_HASH));

        // copy the jar from build/libs to the gradle directory
        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-certs/build/libs/gradle-jdks-certs-all-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                gradleDirectory.resolve("jdks/gradle-jdk-certs.jar"));

        // copy the gradle-jdk-resolver.sh to the gradle directory
        Files.copy(
                Path.of("../gradle-jdks-certs/src/main/resources/gradle-jdk-resolver.sh"),
                gradleDirectory.resolve("gradle-jdk-resolver.sh"));
        // copy the gradle-jdk-resolver.sh to the gradle directory
        Files.copy(
                Path.of("../gradle-jdks-certs/src/main/resources/just-move.sh"),
                gradleDirectory.resolve("just-move.sh"));
        Files.copy(
                Path.of("src/jdkIntegrationTest/resources/testing-script.sh"),
                gradleDirectory.resolve("testing-script.sh"));
        return gradleDirectory;
    }

    private static void writeFileContent(Path path, String content) throws IOException {
        Files.writeString(path, content + "\n");
    }

    private String execInDocker(String dockerImage, String bashEntryPoint, Path localGradlePath, String script)
            throws IOException, InterruptedException {
        return runCommandWithZeroExitCode(List.of(
                "docker",
                "run",
                "--rm",
                "-v",
                String.format(String.format("%s:/gradle", localGradlePath.toAbsolutePath())),
                "--entrypoint",
                bashEntryPoint,
                dockerImage,
                String.format("/gradle/%s", script)));
    }

    static String runCommandWithZeroExitCode(List<String> commandArguments) throws InterruptedException, IOException {
        Process process = new ProcessBuilder()
                .command(commandArguments)
                .redirectErrorStream(true)
                .start();
        String output = readAllInput(process.getInputStream());
        assertThat(process.waitFor())
                .as("Command '%s' failed with output: %s", String.join(" ", commandArguments), output)
                .isEqualTo(0);
        return output;
    }

    private static String readAllInput(InputStream inputStream) {
        try (Stream<String> lines =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    private static String getJavaVersion(String jdkVersion) {
        String[] jdkVersions = jdkVersion.split("\\.");
        return String.join(".", List.of(jdkVersions[0], jdkVersions[1], jdkVersions[2]));
    }
}
