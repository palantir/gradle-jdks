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

import com.palantir.gradle.jdks.setup.FileUtils;
import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.platform.OperatingSystem;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class GenerateGradleJdksConfigsTask extends GradleJdksConfigs {

    private static final Logger log = Logging.getLogger(GenerateGradleJdksConfigsTask.class);

    @OutputDirectory
    public abstract DirectoryProperty getOutputGradleDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutputJdksFile();

    @Inject
    public abstract Gradle getGradle();

    @Override
    protected final Directory gradleDirectory() {
        return getOutputGradleDirectory().get();
    }

    @Override
    protected final void maybePrepareForAction(List<Path> targetPaths) {
        targetPaths.forEach(FileUtils::delete);
        try {
            Files.writeString(
                    getOutputJdksFile().getAsFile().get().toPath(),
                    "",
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected final void applyGradleJdkFileAction(
            Path downloadUrlPath,
            Path localUrlPath,
            JdkDistributionConfig jdkDistribution,
            JavaLanguageVersion javaLanguageVersion) {
        GradleJdksConfigsUtils.createDirectories(downloadUrlPath.getParent());
        GradleJdksConfigsUtils.writeConfigurationFile(
                downloadUrlPath, jdkDistribution.getDownloadUrl().get());
        GradleJdksConfigsUtils.writeConfigurationFile(
                localUrlPath, jdkDistribution.getLocalPath().get());
        if (jdkDistribution.getArch().get().equals(CurrentArch.get())
                && jdkDistribution.getOs().get().equals(OperatingSystem.get())) {
            try {
                Files.writeString(
                        getOutputJdksFile().getAsFile().get().toPath(),
                        String.format(
                                "%s:%s\n",
                                javaLanguageVersion.asInt(),
                                getGradle()
                                        .getGradleUserHomeDir()
                                        .toPath()
                                        .resolve("gradle-jdks")
                                        .resolve(jdkDistribution.getLocalPath().get())
                                        .toAbsolutePath()),
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        String.format(
                                "Failed to write configuration file %s",
                                getOutputJdksFile().getAsFile().get().toPath()),
                        e);
            }
        }
    }

    @Override
    protected final void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion) {
        GradleJdksConfigsUtils.writeConfigurationFile(
                gradleJdkDaemonVersion, getDaemonJavaVersion().get().toString());
    }

    @Override
    protected final void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName) {
        GradleJdksConfigsUtils.writeResourceAsStreamToFile(resourceName, gradleJdkJarFile);
    }

    @Override
    protected final void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName) {
        GradleJdksConfigsUtils.writeResourceAsStreamToFile(resourceName, gradleJdkScriptFile);
        GradleJdksConfigsUtils.setExecuteFilePermissions(gradleJdkScriptFile.toPath());
    }
}
