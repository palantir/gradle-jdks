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

package com.palantir.gradle.jdks.setup;

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

public class GradleJdkInstallationSetupIntegrationTest {

    private static final String SUCCESSFUL_OUTPUT = "Successfully installed JDK distribution, setting JAVA_HOME to";
    private static final String JDK_VERSION = "11.0.21.9.1";
    private static final Arch ARCH = CurrentArch.get();
    private static final String TEST_HASH = "integration-tests";
    private static final String PALANTIR_CERT_ALIAS = "Palantir3rdGenRootCaIntegrationTest";
    private static final String NON_EXISTING_CERT_ALIAS = "NonExistingCert";
    private static final String CORRETTO_DISTRIBUTION_URL_ENV = "CORRETTO_DISTRIBUTION_URL";
    private static final AmazonCorrettoJdkDistribution CORRETTO_JDK_DISTRIBUTION = new AmazonCorrettoJdkDistribution();
    private static final List<String> NO_EXTRA_RUN_STEPS = List.of();
    private static final List<String> INSTALL_CURL_RUN_STEP = List.of(getAptGetCurlRunStep());

    private Path workingDir;

    @BeforeEach
    public void beforeEach() throws IOException {
        workingDir = Files.createTempDirectory("testing-jdk-installation");
    }

    @AfterEach
    public void afterEach() throws IOException {
        FileUtils.deleteDirectory(workingDir.toFile());
    }

    @Test
    public void can_setup_jdk_with_certs_centos() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_GLIBC);
        assertJdkWithNoCertsWasSetUp(dockerBuildAndRunTestingScript("centos:7", "/bin/bash", NO_EXTRA_RUN_STEPS));
    }

    @Test
    public void can_setup_jdk_with_certs_ubuntu() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_GLIBC);
        assertJdkWithNoCertsWasSetUp(
                dockerBuildAndRunTestingScript("ubuntu:20.04", "/bin/bash", INSTALL_CURL_RUN_STEP));
    }

    @Test
    public void can_setup_jdk_with_certs_alpine() throws IOException, InterruptedException {
        setupGradleDirectoryStructure(JDK_VERSION, Os.LINUX_MUSL);
        assertJdkWithNoCertsWasSetUp(dockerBuildAndRunTestingScript("alpine:3.16.0", "/bin/sh", NO_EXTRA_RUN_STEPS));
    }

    @Test
    @EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    public void can_setup_locally_from_scratch() throws IOException, InterruptedException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, CurrentOs.get());
        Path gradleHomeDir = Files.createTempDirectory("jdkIntegrationTest");
        Path gradleJdksDir = Files.createDirectory(gradleHomeDir.resolve("gradle-jdks"));
        Path expectedJavaHomeVersion =
                gradleJdksDir.resolve(String.format("amazon-corretto-%s-%s", JDK_VERSION, TEST_HASH));
        Path expectedJavaHome = expectedJavaHomeVersion.resolve("bin/java");
        assertThat(runCommandWithZeroExitCode(
                        List.of(
                                "/bin/bash",
                                temporaryGradleDirectory
                                        .resolve("gradle-jdks-setup.sh")
                                        .toAbsolutePath()
                                        .toString()),
                        Map.of(
                                "GRADLE_USER_HOME",
                                gradleHomeDir.toAbsolutePath().toString())))
                .contains(String.format(SUCCESSFUL_OUTPUT + " %s", expectedJavaHomeVersion))
                .contains("Successfully imported CA certificate Palantir3rdGenRootCaIntegrationTest into the JDK"
                        + " truststore")
                .contains(String.format(
                        "Certificates '%s' could not be found in the system keystore. These certificates"
                                + " were not imported.",
                        NON_EXISTING_CERT_ALIAS));
        assertThat(runCommandWithZeroExitCode(
                        List.of(
                                "/bin/bash",
                                temporaryGradleDirectory
                                        .resolve("gradle-jdks-setup.sh")
                                        .toAbsolutePath()
                                        .toString()),
                        Map.of(
                                "GRADLE_USER_HOME",
                                gradleHomeDir.toAbsolutePath().toString())))
                .contains(String.format("already exists, setting JAVA_HOME to %s", expectedJavaHomeVersion));
        assertThat(Files.exists(expectedJavaHome)).isTrue();
        FileUtils.deleteDirectory(workingDir.toFile());
    }

    private Path setupGradleDirectoryStructure(String jdkVersion, Os os) throws IOException {
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
         * │   ├── certs/
         * │   │   ├── Palantir3rdGenRootCa.serial-number
         * │   ├── gradle-jdk-major-version
         * │   ├── gradle-jdks-setup.jar
         * ├── subProjects/...
         * ...
         */
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(jdkVersion), 0);
        Path gradleDirectory = Files.createDirectories(workingDir.resolve("gradle"));
        Path gradleJdkVersion = Files.createFile(gradleDirectory.resolve("gradle-jdk-major-version"));
        writeFileContent(gradleJdkVersion, jdkMajorVersion.toString());
        JdkPath jdkPath = CORRETTO_JDK_DISTRIBUTION.path(
                JdkRelease.builder().version(jdkVersion).os(os).arch(ARCH).build());
        Path archDirectory = Files.createDirectories(
                gradleDirectory.resolve(String.format("jdks/%s/%s/%s", jdkMajorVersion, os.uiName(), ARCH.uiName())));
        Path certsDirectory = Files.createDirectories(gradleDirectory.resolve("certs"));
        Path palantirCert =
                Files.createFile(certsDirectory.resolve("Palantir3rdGenRootCaIntegrationTest.serial-number"));
        writeFileContent(palantirCert, "18126334688741185161");
        Path nonExistingCert =
                Files.createFile(certsDirectory.resolve(String.format("%s.serial-number", NON_EXISTING_CERT_ALIAS)));
        writeFileContent(nonExistingCert, "1111");
        Path downloadUrlPath = Files.createFile(archDirectory.resolve("download-url"));
        String correttoDistributionUrl = Optional.ofNullable(System.getenv(CORRETTO_DISTRIBUTION_URL_ENV))
                .orElseGet(CORRETTO_JDK_DISTRIBUTION::defaultBaseUrl);
        writeFileContent(
                downloadUrlPath,
                String.format(
                        String.format("%s/%s.%s", correttoDistributionUrl, jdkPath.filename(), jdkPath.extension())));
        Path localPath = Files.createFile(archDirectory.resolve("local-path"));
        writeFileContent(localPath, String.format("amazon-corretto-%s-%s", jdkVersion, TEST_HASH));

        // copy the jar from build/libs to the gradle directory
        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                gradleDirectory.resolve("jdks/gradle-jdks-setup.jar"));

        // copy the gradle-jdks-setup.sh to the gradle directory
        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                gradleDirectory.resolve("gradle-jdks-setup.sh"));

        // copy the testing script to the gradle directory
        Files.copy(
                Path.of("src/jdkIntegrationTest/resources/testing-script.sh"),
                gradleDirectory.resolve("testing-script.sh"));

        // workaround when running locally to ignore the certificate setup when using curl & wget
        if (Optional.ofNullable(System.getenv("CI")).isEmpty()) {
            Files.copy(
                    Path.of("src/jdkIntegrationTest/resources/ignore-certs-curl-wget.sh"),
                    gradleDirectory.resolve("ignore-certs-curl-wget.sh"));
        }

        return gradleDirectory;
    }

    private static void writeFileContent(Path path, String content) throws IOException {
        Files.writeString(path, content + "\n");
    }

    private String dockerBuildAndRunTestingScript(String baseImage, String shell, List<String> dockerRunSteps)
            throws IOException, InterruptedException {
        Path resourcesPath = Path.of("src/jdkIntegrationTest/resources/");
        Path renderedDockerfile = resourcesPath.resolve("Dockerfile.jdkIntegrationTest.rendered");
        Files.writeString(
                renderedDockerfile,
                Files.readString(Path.of("src/jdkIntegrationTest/resources/Dockerfile.template"))
                        .replaceAll("@BASE_IMAGE@", baseImage)
                        .replaceAll("@SHELL@", shell)
                        .replaceAll("@EXTRA_RUN_STEPS@", getAllRunSteps(dockerRunSteps)));
        String dockerImage = String.format("jdk-test-%s", baseImage);
        runCommandWithZeroExitCode(List.of(
                "docker",
                "build",
                "-t",
                dockerImage,
                "-f",
                renderedDockerfile.toAbsolutePath().toString(),
                workingDir.toAbsolutePath().toString()));
        return runCommandWithZeroExitCode(List.of("docker", "run", dockerImage));
    }

    private static String getAllRunSteps(List<String> dockerRunSteps) {
        if (dockerRunSteps.isEmpty()) {
            return "";
        }
        return String.format("RUN %s\n", dockerRunSteps.stream().collect(Collectors.joining(" && ")));
    }

    private static String getAptGetCurlRunStep() {
        return "apt-get update && apt-get install -y curl";
    }

    static String runCommandWithZeroExitCode(List<String> commandArguments) throws InterruptedException, IOException {
        return runCommandWithZeroExitCode(commandArguments, Map.of());
    }

    static String runCommandWithZeroExitCode(List<String> commandArguments, Map<String, String> environment)
            throws InterruptedException, IOException {
        ProcessBuilder processBuilder =
                new ProcessBuilder().command(commandArguments).redirectErrorStream(true);
        processBuilder.environment().putAll(Objects.requireNonNull(environment));
        Process process = processBuilder.start();
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

    private static void assertJdkWithNoCertsWasSetUp(String output) {
        String expectedDistributionPath =
                String.format("/root/.gradle/gradle-jdks/amazon-corretto-%s-%s", JDK_VERSION, TEST_HASH);
        assertThat(output)
                .contains(SUCCESSFUL_OUTPUT)
                .contains(String.format("Java home is: %s", expectedDistributionPath))
                .contains(String.format("Java path is: %s", expectedDistributionPath))
                .contains(String.format("Java version is: %s", getJavaVersion(JDK_VERSION)))
                .contains(String.format(
                        "Certificates '%s, %s' could not be found in the system keystore. These"
                                + " certificates were not imported.",
                        NON_EXISTING_CERT_ALIAS, PALANTIR_CERT_ALIAS));
    }
}
