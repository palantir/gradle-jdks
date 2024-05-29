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

import com.palantir.gradle.jdks.setup.CaResources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.OutputDirectory;

public abstract class GenerateGradleJdkConfigsTask extends GradleJdkConfigs {

    private static final Logger log = Logging.getLogger(GenerateGradleJdkConfigsTask.class);

    @OutputDirectory
    public abstract DirectoryProperty getOutputGradleDirectory();

    @Override
    public Directory gradleDirectory() {
        return getOutputGradleDirectory().get();
    }

    @Override
    public void applyGradleJdkFileAction(
            Path downloadUrlPath, Path localUrlPath, JdkDistributionConfig jdkDistribution) {
        try {
            Files.createDirectories(downloadUrlPath.getParent());
            writeConfigurationFile(
                    downloadUrlPath, jdkDistribution.getDownloadUrl().get());
            writeConfigurationFile(localUrlPath, jdkDistribution.getLocalPath().get());
        } catch (IOException e) {
            throw new RuntimeException("Failed to crate the jdk configuration file", e);
        }
    }

    @Override
    public void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion) {
        try {
            writeConfigurationFile(
                    gradleJdkDaemonVersion, getDaemonJavaVersion().get().toString());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write `%s`", gradleJdkDaemonVersion), e);
        }
    }

    @Override
    public void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName) {
        writeResourceStreamToFile(resourceName, gradleJdkJarFile);
    }

    @Override
    public void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName) {
        try {
            writeResourceStreamToFile(resourceName, gradleJdkScriptFile);
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(gradleJdkScriptFile.toPath());
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(gradleJdkScriptFile.toPath(), permissions);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add the gradle jdk script in `gradle/`", e);
        }
    }

    @Override
    public void applyCertAction(File certFile, String alias, String content) {
        try {
            Files.createDirectories(certFile.getParentFile().toPath());
            writeConfigurationFile(certFile.toPath(), CaResources.getSerialNumber(content));
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Failed to generate the certificate `%s` configuration in` gradle/certs`", alias), e);
        }
    }

    private static void writeResourceStreamToFile(String resource, File outputFile) {
        try (InputStream inputStream =
                GenerateGradleJdkConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }

            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                inputStream.transferTo(outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write %s to %s", resource, outputFile.toPath()), e);
        }
    }

    private static void writeConfigurationFile(Path pathFile, String content) throws IOException {
        if (!Files.exists(pathFile)) {
            Files.createFile(pathFile);
        }
        // The content of the configuration files should always end with a newline character to ensure the file can be
        // read by {see: resources/gradle-jdks-setup.sh#read_value }
        String contentWithLineEnding = content + "\n";
        Files.write(pathFile, contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
    }
}
