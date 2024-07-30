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

import com.palantir.gradle.jdks.setup.common.CommandRunner;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleJdksConfiguratorTest {

    private static final String JDK_VERSION = "21.0.3.9.1";

    @TempDir
    Path latestGradleJdksDir;

    @TempDir
    Path symlinkDir;

    @Test
    void can_install_generated_gradle_jdk_files() throws IOException {
        GradleJdksConfigurator.writeGradleJdkInstallationConfigs(
                latestGradleJdksDir,
                "https://corretto.aws",
                JdkDistributionName.AMAZON_CORRETTO,
                CurrentOs.get(),
                CurrentArch.get(),
                "21",
                JDK_VERSION,
                Map.of());
        GradleJdksConfigurator.writeInstallationScripts(latestGradleJdksDir);

        Files.exists(latestGradleJdksDir
                .resolve("jdks")
                .resolve("21")
                .resolve(CurrentOs.get().uiName())
                .resolve(CurrentArch.get().uiName())
                .resolve("download-url"));
        String localPath = Files.readString(latestGradleJdksDir
                .resolve("jdks")
                .resolve("21")
                .resolve(CurrentOs.get().uiName())
                .resolve(CurrentArch.get().uiName())
                .resolve("local-path"));
        assertThat(localPath).containsPattern(String.format("amazon-corretto-%s-([a-zA-Z0-9])+\n", JDK_VERSION));
        Path installationScript = latestGradleJdksDir.resolve("scripts").resolve("install-jdks.sh");
        Path certsDir = Files.createDirectories(latestGradleJdksDir.resolve("certs"));
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(
                        installationScript.toAbsolutePath().toString(),
                        latestGradleJdksDir.toString(),
                        certsDir.toString(),
                        symlinkDir.toString());
        Path installationJdkDir = latestGradleJdksDir.resolve("installed-jdks");
        processBuilder.environment().put("GRADLE_USER_HOME", installationJdkDir.toString());
        CommandRunner.runWithOutputCollection(processBuilder);
        Path installedJdkPath = installationJdkDir.resolve("gradle-jdks").resolve(localPath.trim());
        ProcessBuilder runJavaCommand = new ProcessBuilder()
                .command(findJavaExec(installedJdkPath).toAbsolutePath().toString(), "-version")
                .redirectErrorStream(true);
        assertThat(CommandRunner.runWithOutputCollection(runJavaCommand))
                .contains(String.format("Corretto-%s", JDK_VERSION));
        assertThat(symlinkDir.resolve("java21").toRealPath()).isEqualTo(installedJdkPath.toRealPath());
    }

    private static Path findJavaExec(Path javaHome) throws IOException {
        try (Stream<Path> stream = Files.walk(javaHome)) {
            return stream.filter(path -> path.getFileName().toString().equals("java") && Files.isExecutable(path))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find java executable in " + javaHome));
        }
    }
}
