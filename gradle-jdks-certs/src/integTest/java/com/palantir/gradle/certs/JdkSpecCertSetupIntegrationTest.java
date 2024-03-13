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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class JdkSpecCertSetupIntegrationTest {

    private static final String JDK_VERSION = "11.0.21.9.1";
    private static final String ARCH = getArch();

    @Test
    public void can_setup_jdk_with_certs_centos() throws IOException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, "linux");
        assertThat(execInDocker("centos:7", "/bin/bash", temporaryGradleDirectory))
                .isEqualTo("sasa");
    }

    @Test
    public void can_setup_jdk_with_certs_ubuntu() throws IOException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, "linux");
        assertThat(execInDocker("ubuntu:20.04", "/bin/bash", temporaryGradleDirectory))
                .isEqualTo("sasa");
    }

    @Test
    public void can_setup_jdk_with_certs_alpine() throws IOException {
        Path temporaryGradleDirectory = setupGradleDirectoryStructure(JDK_VERSION, "alpine-linux");
        assertThat(execInDocker("alpine:3.16.0", "/bin/sh", temporaryGradleDirectory))
                .isEqualTo("sasa");
    }

    private static Path setupGradleDirectoryStructure(String jdkVersion, String os) throws IOException {
        /**
         * Each project will contain the following gradle file structure:
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
        Files.writeString(gradleJdkVersion, jdkMajorVersion.toString());
        Path archDirectory = Files.createDirectories(
                gradleDirectory.resolve(String.format("jdks/%s/%s/%s", jdkMajorVersion, os, ARCH)));
        Path downloadUrlPath = Files.createFile(archDirectory.resolve("download-url"));
        Files.writeString(
                downloadUrlPath,
                String.format(
                        "https://corretto.aws/downloads/resources/%s/amazon-corretto-%s-%s-%s.tar.gz",
                        jdkVersion, jdkVersion, os, ARCH));
        Path localPath = Files.createFile(archDirectory.resolve("local-path"));
        Files.writeString(localPath, String.format("amazon-corretto-%s-crogoz", jdkVersion));

        // copy the jar from build/libs to the gradle directory
        Files.copy(
                Path.of(String.format(
                        "build/libs/gradle-jdks-certs-%s.jar", System.getenv().get("PROJECT_VERSION"))),
                gradleDirectory.resolve("jdks/gradle-jdk-certs.jar"));

        // copy the gradle-jdk-resolver.sh to the gradle directory
        Files.copy(
                Path.of("src/main/resources/gradle-jdk-resolver.sh"),
                gradleDirectory.resolve("gradle-jdk-resolver.sh"));
        return gradleDirectory;
    }

    private String execInDocker(String dockerImage, String bashEntryPoint, Path localGradlePath) {
        return runCommand(List.of(
                "docker",
                "run",
                "--rm",
                "-v",
                String.format(String.format("%s:/gradle", localGradlePath.toAbsolutePath())),
                "--entrypoint",
                bashEntryPoint,
                dockerImage,
                "/gradle/gradle-jdk-resolver.sh"));
    }

    static String runCommand(List<String> commandArguments) {
        try {
            Process process = new ProcessBuilder()
                    .command(commandArguments)
                    .redirectErrorStream(true)
                    .start();
            String output = readAllInput(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Failed to run command '%s'. " + "Failed with exit code %d.Output:\n\n%s",
                        String.join(" ", commandArguments), exitCode, output));
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        } catch (InterruptedException e) {
            throw new RuntimeException(
                    String.format("Failed to run command '%s'. ", String.join(" ", commandArguments)), e);
        }
    }

    private static String readAllInput(InputStream inputStream) {
        try (Stream<String> lines =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    private static String getArch() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (Set.of("x86_64", "x64", "amd64").contains(osArch)) {
            return "x86-64";
        }

        if (Set.of("arm", "arm64", "aarch64").contains(osArch)) {
            return "aarch64";
        }

        if (Set.of("x86", "i686").contains(osArch)) {
            return "x86";
        }
        throw new RuntimeException("TODO not supported");
    }
}
