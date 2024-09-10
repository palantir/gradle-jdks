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

import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;

public abstract class CheckGradleJdksConfigsTask extends GradleJdksConfigs {

    @InputDirectory
    public abstract DirectoryProperty getInputGradleDirectory();

    @OutputFile
    public abstract RegularFileProperty getDummyOutputFile();

    @Override
    protected final Directory gradleDirectory() {
        return getInputGradleDirectory().get();
    }

    @Override
    protected void maybePrepareForAction(List<Path> targetPaths) {}

    @Override
    protected final void applyGradleJdkFileAction(
            Path downloadUrlPath, Path localUrlPath, JdkDistributionConfig jdkDistribution) {
        assertFileContent(downloadUrlPath, jdkDistribution.getDownloadUrl().get());
        assertFileContent(localUrlPath, jdkDistribution.getLocalPath().get());
    }

    @Override
    protected final void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion) {
        assertFileContent(gradleJdkDaemonVersion, getDaemonJavaVersion().get().toString());
    }

    @Override
    protected final void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName) {
        try {
            byte[] expectedBytes = getResourceContent(resourceName);
            byte[] actualBytes = Files.readAllBytes(gradleJdkJarFile.toPath());
            checkOrThrow(Arrays.equals(expectedBytes, actualBytes), gradleJdkJarFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("An error occurred while checking the gradle jdk setup jar", e);
        }
    }

    @Override
    protected final void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName) {
        assertFileContent(
                gradleJdkScriptFile.toPath(), new String(getResourceContent(resourceName), StandardCharsets.UTF_8));
    }

    private static void assertFileContent(Path filePath, String expectedContent) {
        checkOrThrow(
                filePath.toFile().exists() && readConfigurationFile(filePath).equals(expectedContent.trim()), filePath);
    }

    private static String readConfigurationFile(Path filePath) {
        try {
            return Files.readString(filePath).trim();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read file %s", filePath), e);
        }
    }

    private static void checkOrThrow(boolean result, Path outOfDateFile) {
        if (!result) {
            throw new ExceptionWithSuggestion(
                    String.format(
                            "Gradle JDK configuration file `%s` is out of date, please run `./gradlew setupJdks`",
                            getRelativeToGradleFile(outOfDateFile)),
                    "./gradlew setupJdks");
        }
    }

    private static String getRelativeToGradleFile(Path file) {
        Path gradlePath = file;
        while (gradlePath != null && !gradlePath.getFileName().toString().equals("gradle")) {
            gradlePath = gradlePath.getParent();
        }
        return gradlePath != null ? gradlePath.getParent().relativize(file).toString() : file.toString();
    }

    public static byte[] getResourceContent(String resource) {
        try (InputStream inputStream =
                GenerateGradleJdksConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to retrieve resource %s", resource), e);
        }
    }
}
