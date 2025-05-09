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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.GradleJdksPatchHelper;
import com.palantir.gradle.jdks.setup.common.Os;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GradleJdkInstallationSetupIntegrationTest {

    private static final String JDK_VERSION = "11.0.21.9.1";
    private static final String GRAAL_VERSION = "23.0.1";
    private static final Arch ARCH = CurrentArch.get();
    private static final String CORRETTO_DISTRIBUTION_URL_ENV = "CORRETTO_DISTRIBUTION_URL";
    private static final AmazonCorrettoJdkDistribution CORRETTO_JDK_DISTRIBUTION = new AmazonCorrettoJdkDistribution();
    private static final GraalVmCeDistribution GRAAL_VM_CE_DISTRIBUTION = new GraalVmCeDistribution();
    private static final boolean DO_NOT_INSTALL_CURL = false;
    private static final boolean INSTALL_CURL = true;

    @TempDir
    private Path workingDir;

    @Test
    public void can_setup_jdks_centos_using_wget() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(Os.LINUX_GLIBC);
        dockerBuildAndRunTestingScript("centos:7", "/bin/bash", DO_NOT_INSTALL_CURL, false, true);
    }

    @Test
    public void can_setup_jdks_ubuntu_using_curl() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(Os.LINUX_GLIBC);
        dockerBuildAndRunTestingScript("ubuntu:20.04", "/bin/bash", INSTALL_CURL, false, true);
    }

    @Test
    public void can_reinstall_jdks_ubuntu_using_curl() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(Os.LINUX_GLIBC);
        dockerBuildAndRunTestingScript("ubuntu:20.04", "/bin/bash", INSTALL_CURL, true, true);
    }

    @Test
    public void can_setup_jdks_alpine() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(Os.LINUX_MUSL);
        dockerBuildAndRunTestingScript("alpine:3.16.0", "/bin/sh", DO_NOT_INSTALL_CURL, false, false);
    }

    private Path setupGradleDirectoryStructure(Os os) throws IOException {
        /**
         * Each project will contain the following gradle file structure:
         * Note! Make sure the files end in a newline character, otherwise the `read` command in the
         * gradle-jdks-setup.sh will fail!
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
         * │   ├── gradle-daemon-jdk-version
         * │   ├── gradle-jdks-setup.sh
         * │   ├── gradle-jdks-setup.jar
         * ├── subProjects/...
         * ...
         */
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(JDK_VERSION), 0);
        Path gradleDirectory = Files.createDirectories(workingDir.resolve("gradle"));
        Path gradleJdkVersion = Files.createFile(gradleDirectory.resolve("gradle-daemon-jdk-version"));
        writeFileContent(gradleJdkVersion, jdkMajorVersion);
        JdkPath jdkPath = CORRETTO_JDK_DISTRIBUTION.path(JdkRelease.builder()
                .version(GradleJdkInstallationSetupIntegrationTest.JDK_VERSION)
                .os(os)
                .arch(ARCH)
                .build());
        Path archDirectory = Files.createDirectories(
                gradleDirectory.resolve(String.format("jdks/%s/%s/%s", jdkMajorVersion, os.uiName(), ARCH.uiName())));

        // Adding an Amazon Corretto distribution
        Path downloadUrlPath = Files.createFile(archDirectory.resolve("download-url"));
        String correttoDistributionUrl = Optional.ofNullable(System.getenv(CORRETTO_DISTRIBUTION_URL_ENV))
                .orElseGet(CORRETTO_JDK_DISTRIBUTION::defaultBaseUrl);
        writeFileContent(
                downloadUrlPath,
                String.format(
                        String.format("%s/%s.%s", correttoDistributionUrl, jdkPath.filename(), jdkPath.extension())));
        Path localPath = Files.createFile(archDirectory.resolve("local-path"));
        writeFileContent(localPath, String.format("amazon-corretto-%s", JDK_VERSION));

        if (!os.equals(Os.LINUX_MUSL)) {
            // Adding a GraalVm distribution only for non-musl
            String graalMajorVersion = Iterables.get(Splitter.on('.').split(GRAAL_VERSION), 0);
            Path graalDirectory = Files.createDirectories(gradleDirectory.resolve(
                    String.format("jdks/%s/%s/%s", graalMajorVersion, os.uiName(), ARCH.uiName())));
            Path graalDownloadUrlPath = Files.createFile(graalDirectory.resolve("download-url"));
            JdkPath graalJdkPath = GRAAL_VM_CE_DISTRIBUTION.path(JdkRelease.builder()
                    .version(GRAAL_VERSION)
                    .os(os)
                    .arch(ARCH)
                    .build());
            writeFileContent(
                    graalDownloadUrlPath,
                    String.format(String.format(
                            "%s/%s.%s",
                            GRAAL_VM_CE_DISTRIBUTION.defaultBaseUrl(),
                            graalJdkPath.filename(),
                            graalJdkPath.extension())));
            Path graalLocalPath = Files.createFile(graalDirectory.resolve("local-path"));
            writeFileContent(graalLocalPath, String.format("graalvm-community-jdk-%s", GRAAL_VERSION));
        }
        // copy the jar from build/libs to the gradle directory
        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-all-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                gradleDirectory.resolve("gradle-jdks-setup.jar"));

        // copy gradle-jdks-setup.sh to the gradle directory
        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                gradleDirectory.resolve("gradle-jdks-setup.sh"));

        // copy gradle-jdks-functions.sh" to the gradle directory
        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-functions.sh"),
                gradleDirectory.resolve("gradle-jdks-functions.sh"));

        // copy the testing script to the working directory
        List<String> gradlewPatchLines =
                Files.readAllLines(Path.of("../gradle-jdks/src/main/resources/gradlew-patch.sh"));
        List<String> initialTestLines =
                Files.readAllLines(Path.of("src/integTest/resources/testing-script.template.sh"));
        int placeholderIndex = IntStream.range(0, initialTestLines.size())
                .filter(lineNo -> initialTestLines.get(lineNo).equals("PLACEHOLDER_INSERT_GRADLEW_PATCH"))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("unable to find PLACEHOLDER_INSERT_GRADLEW_PATCH in testing-script.sh"));
        initialTestLines.remove(placeholderIndex);
        GradleJdksPatchHelper.writeContentWithPatch(
                workingDir.resolve("testing-script.sh"), initialTestLines, gradlewPatchLines, placeholderIndex);

        return gradleDirectory;
    }

    private static void writeFileContent(Path path, String content) throws IOException {
        Files.writeString(path, content + "\n");
    }

    private void dockerBuildAndRunTestingScript(
            String baseImage, String shell, boolean installCurl, boolean addJdkDir, boolean expectedGraalJdk)
            throws IOException, InterruptedException {
        Path dockerFile = Path.of("src/integTest/resources/template.Dockerfile");
        String dockerImage = String.format("jdk-test-%s", baseImage);
        runCommandWithZeroExitCode(List.of(
                "docker",
                "build",
                "--build-arg",
                String.format("BASE_IMAGE=%s", baseImage),
                "--build-arg",
                String.format("INSTALL_CURL=%s", installCurl),
                "--build-arg",
                String.format("SCRIPT_SHELL=%s", shell),
                "--build-arg",
                String.format("ADD_JDK_DIR=%s", addJdkDir),
                "-t",
                dockerImage,
                "-f",
                dockerFile.toAbsolutePath().toString(),
                workingDir.toAbsolutePath().toString()));
        String output =
                runCommandWithZeroExitCode(List.of("docker", "run", "--rm", dockerImage, shell, "/testing-script.sh"));
        assertThat(output)
                .contains("openjdk version \"11.0.21\"")
                .contains("JAVA_HOME is set to: /root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1")
                .doesNotContain("Unexpected output");
        if (expectedGraalJdk) {
            assertThat(output).contains("GraalVM CE 23.0.1");
        } else {
            assertThat(output).contains("GraalVM is not set");
        }
    }

    private static String runCommandWithZeroExitCode(List<String> commandArguments)
            throws InterruptedException, IOException {
        return runCommandWithZeroExitCode(commandArguments, Map.of());
    }

    private static String runCommandWithZeroExitCode(List<String> commandArguments, Map<String, String> environment)
            throws InterruptedException, IOException {
        ProcessBuilder processBuilder =
                new ProcessBuilder().command(commandArguments).redirectErrorStream(true);
        processBuilder.environment().putAll(Objects.requireNonNull(environment));
        Process process = processBuilder.start();
        String output = CommandRunner.readAllInput(process.getInputStream());
        assertThat(process.waitFor())
                .as("Command '%s' failed with output: %s", String.join(" ", commandArguments), output)
                .isEqualTo(0);
        return output;
    }
}
