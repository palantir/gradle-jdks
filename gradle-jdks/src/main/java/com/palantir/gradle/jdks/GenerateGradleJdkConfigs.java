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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public abstract class GenerateGradleJdkConfigs {

    private static final Logger log = Logging.getLogger(GenerateGradleJdkConfigs.class);

    interface Params {

        @Nested
        MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

        @Input
        Property<JavaLanguageVersion> getDaemonJavaVersion();

        @Input
        MapProperty<String, String> getCaCerts();

        @OutputDirectory
        DirectoryProperty getOutputGradleDirectory();
    }

    public abstract static class GenerateGradleJdkConfigsTask extends GenerateGradleJdkConfigsTaskImpl {}

    static void action(Params params) {
        // TODO(crogoz): delete everything that was not touched ?
        params.getJavaVersionToJdkDistros().get().forEach((javaVersion, jdkDistros) -> {
            jdkDistros.forEach(jdkDistro ->
                    createJdkFiles(params.getOutputGradleDirectory().get(), javaVersion, jdkDistro));
        });
        addGradleJdkDaemonVersion(
                params.getOutputGradleDirectory().get(),
                params.getDaemonJavaVersion().get());
        addGradleJdkJarTo(params.getOutputGradleDirectory().get());
        addGradleJdkSetupScriptTo(params.getOutputGradleDirectory().get());
        addCerts(params.getOutputGradleDirectory().get(), params.getCaCerts().get());
    }

    private static void addGradleJdkDaemonVersion(Directory gradleDirectory, JavaLanguageVersion daemonJavaVersion) {
        try {
            writeConfigurationFile(
                    gradleDirectory.getAsFile().toPath().resolve(GradleJdkConfigResources.GRADLE_DAEMON_JDK_VERSION),
                    daemonJavaVersion.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write `gradle/gradle-jdk-daemon-version`", e);
        }
    }

    private static void addCerts(Directory gradleDirectory, Map<String, String> caCerts) {
        try {
            File certsDir = gradleDirectory.file(GradleJdkConfigResources.CERTS).getAsFile();
            Files.createDirectories(certsDir.toPath());
            caCerts.forEach((alias, content) -> {
                try {
                    File certFile = new File(certsDir, String.format("%s.serial-number", alias));
                    writeConfigurationFile(certFile.toPath(), CaResources.getSerialNumber(content));
                } catch (IOException e) {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to generate the certificate `%s` configuration in` gradle/certs`", alias),
                            e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate certificate configurations in `gradle/certs`", e);
        }
    }

    private static void addGradleJdkSetupScriptTo(Directory gradleDirectory) {
        try {
            File gradleJdksSetupScript = gradleDirectory
                    .file(GradleJdkConfigResources.GRADLE_JDKS_SETUP_SCRIPT)
                    .getAsFile();
            writeResourceStreamToFile(GradleJdkConfigResources.GRADLE_JDKS_SETUP_SCRIPT, gradleJdksSetupScript);
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(gradleJdksSetupScript.toPath());
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(gradleJdksSetupScript.toPath(), permissions);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add the gradle jdk script in `gradle/`", e);
        }
    }

    private static void addGradleJdkJarTo(Directory gradleDirectory) {
        writeResourceStreamToFile(
                GradleJdkConfigResources.GRADLE_JDKS_SETUP_JAR,
                gradleDirectory
                        .file(GradleJdkConfigResources.GRADLE_JDKS_SETUP_JAR)
                        .getAsFile());
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

    private static void createJdkFiles(
            Directory gradleDirectory, JavaLanguageVersion javaVersion, JdkDistributionConfig jdkDistribution) {
        Path outputDir = gradleDirectory
                .dir(GradleJdkConfigResources.JDKS_DIR)
                .getAsFile()
                .toPath()
                .resolve(javaVersion.toString())
                .resolve(jdkDistribution.getOs().get().uiName())
                .resolve(jdkDistribution.getArch().get().uiName());
        try {
            Files.createDirectories(outputDir);
            Path downloadUrlPath = outputDir.resolve(GradleJdkConfigResources.DOWNLOAD_URL);
            writeConfigurationFile(
                    downloadUrlPath, jdkDistribution.getDownloadUrl().get());
            Path localPath = outputDir.resolve(GradleJdkConfigResources.LOCAL_PATH);
            writeConfigurationFile(localPath, jdkDistribution.getLocalPath().get());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to crate the jdk configuration file %s", outputDir), e);
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
