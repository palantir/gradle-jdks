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

import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.jdks.setup.CaResources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public abstract class CheckGradleJdkConfigs {

    interface Params {

        @Nested
        MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

        @Input
        Property<JavaLanguageVersion> getDaemonJavaVersion();

        @Input
        MapProperty<String, String> getCaCerts();

        @InputDirectory
        DirectoryProperty getInputGradleDirectory();

        @OutputFile
        RegularFileProperty getDummyOutputFile();
    }

    public abstract static class CheckGradleJdkConfigsTask extends CheckGradleJdkConfigsTaskImpl {}

    static void action(Params params) {
        params.getJavaVersionToJdkDistros().get().forEach((javaVersion, jdkDistros) -> {
            jdkDistros.forEach(
                    jdkDistro -> checkJdkFiles(params.getInputGradleDirectory().get(), javaVersion, jdkDistro));
        });

        checkGradleJdkDaemon(
                params.getInputGradleDirectory().get(),
                params.getDaemonJavaVersion().get());
        checkGradleJdkJar(params.getInputGradleDirectory().get());
        checkGradleJdkScript(params.getInputGradleDirectory().get());
        checkCerts(params.getInputGradleDirectory().get(), params.getCaCerts().get());
    }

    private static void checkCerts(Directory gradleDirectory, Map<String, String> caCerts) {
        File certsDir = gradleDirectory.file(GradleJdkConfigResources.CERTS).getAsFile();
        caCerts.forEach((alias, content) -> {
            File certFile = new File(certsDir, String.format("%s.serial-number", alias));
            assertFileContent(certFile.toPath(), CaResources.getSerialNumber(content));
        });
    }

    private static void checkJdkFiles(
            Directory gradleDirectory, JavaLanguageVersion javaVersion, JdkDistributionConfig jdkDistribution) {
        Path outputDir = GradleJdkConfigResources.resolveJdkOsArchPath(gradleDirectory, javaVersion, jdkDistribution);
        Path downloadUrlPath = outputDir.resolve(GradleJdkConfigResources.DOWNLOAD_URL);
        assertFileContent(downloadUrlPath, jdkDistribution.getDownloadUrl().get());
        Path localPath = outputDir.resolve(GradleJdkConfigResources.LOCAL_PATH);
        assertFileContent(localPath, jdkDistribution.getLocalPath().get());
    }

    private static void checkGradleJdkDaemon(Directory gradleDirectory, JavaLanguageVersion daemonJavaVersion) {
        assertFileContent(
                gradleDirectory.getAsFile().toPath().resolve(GradleJdkConfigResources.GRADLE_DAEMON_JDK_VERSION),
                daemonJavaVersion.toString());
    }

    private static void checkGradleJdkScript(Directory gradleDirectory) {
        assertFileContent(
                gradleDirectory
                        .file(GradleJdkConfigResources.GRADLE_JDKS_SETUP_SCRIPT)
                        .getAsFile()
                        .toPath(),
                new String(
                        GradleJdkConfigResources.getResourceContent(GradleJdkConfigResources.GRADLE_JDKS_SETUP_SCRIPT),
                        StandardCharsets.UTF_8));
    }

    private static void checkGradleJdkJar(Directory gradleDirectory) {
        try {
            byte[] expectedBytes =
                    GradleJdkConfigResources.getResourceContent(GradleJdkConfigResources.GRADLE_JDKS_SETUP_JAR);
            byte[] actualBytes = Files.readAllBytes(gradleDirectory
                    .file(GradleJdkConfigResources.GRADLE_JDKS_SETUP_JAR)
                    .getAsFile()
                    .toPath());
            if (!Arrays.equals(expectedBytes, actualBytes)) {
                throw new ExceptionWithSuggestion(
                        "Gradle JDK script is out of date, please run `./gradlew generateGradleJdkConfigs`",
                        "./gradlew generateGradleJdkConfigs");
            }
        } catch (IOException e) {
            throw new RuntimeException("An error occurred while checking the gradle jdk setup jar", e);
        }
    }

    private static void assertFileContent(Path filePath, String expectedContent) {
        checkOrThrow(
                filePath.toFile().exists() && readConfigurationFile(filePath).equals(expectedContent.trim()));
    }

    private static String readConfigurationFile(Path filePath) {
        try {
            return Files.readString(filePath).trim();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read file %s", filePath), e);
        }
    }

    private static void checkOrThrow(boolean result) {
        if (!result) {
            throw new ExceptionWithSuggestion(
                    "Gradle JDK configurations are out of date, please run `./gradlew generateGradleJdkConfigs`",
                    "./gradlew generateGradleJdkConfigs");
        }
    }
}
