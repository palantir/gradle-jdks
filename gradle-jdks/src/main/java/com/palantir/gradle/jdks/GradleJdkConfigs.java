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
import java.util.function.Supplier;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public abstract class GradleJdkConfigs {

    private static final Logger log = Logging.getLogger(GradleJdkConfigs.class);
    private static final String JDKS_DIR = "jdks";
    private static final String CERTS_DIR = "certs";
    private static final String DOWNLOAD_URL = "download-url";
    private static final String LOCAL_PATH = "local-path";
    private static final String GRADLE_JDKS_SETUP_JAR = "gradle-jdks-setup.jar";
    private static final String GRADLE_JDKS_SETUP_SCRIPT = "gradle-jdks-setup.sh";
    private static final String GRADLE_DAEMON_JDK_VERSION = "gradle-daemon-jdk-version";
    private static final Supplier<ExceptionWithSuggestion> REGENERATE_FILES_ERROR = () -> new ExceptionWithSuggestion(
            "Gradle JDK configuration is out of date, please run `./gradlew generateGradleJdkConfigs` to update the"
                    + " JDKs",
            "./gradlew generateGradleJdkConfigs");

    interface JdkDistributionConfig {

        @Input
        Property<String> getDownloadUrl();

        @Input
        Property<String> getLocalPath();

        @Input
        Property<Os> getOs();

        @Input
        Property<Arch> getArch();

        @Input
        Property<String> getConsistentHash();
    }

    interface Params {

        @Nested
        MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

        @Input
        Property<JavaLanguageVersion> getDaemonJavaVersion();

        @Input
        Property<Boolean> getGenerate();

        @Input
        MapProperty<String, String> getCaCerts();

        @Optional
        @Internal
        DirectoryProperty getGradleDirectory();

        @OutputDirectory
        DirectoryProperty getOutputGradleDirectory();
    }

    public abstract static class GradleJdkConfigsTask extends GradleJdkConfigsTaskImpl {

        public GradleJdkConfigsTask() {
            this.getGenerate().convention(false);
        }
    }

    static void action(Params params) {
        params.getJavaVersionToJdkDistros().get().forEach((javaVersion, jdkDistros) -> {
            jdkDistros.forEach(jdkDistro ->
                    createJdkFiles(params.getOutputGradleDirectory().get(), javaVersion, jdkDistro));
        });

        try {
            writeToFile(
                    params.getOutputGradleDirectory()
                            .file(GRADLE_DAEMON_JDK_VERSION)
                            .get()
                            .getAsFile()
                            .toPath(),
                    params.getDaemonJavaVersion().get().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        addGradleJdkJarTo(params.getOutputGradleDirectory().get());
        addGradleJdkSetupScriptTo(params.getOutputGradleDirectory().get());
        addCerts(params.getOutputGradleDirectory().get(), params.getCaCerts().get());

        if (!params.getGenerate().get()) {
            FileUtils.checkDirectoriesAreTheSame(
                    params.getOutputGradleDirectory().dir(JDKS_DIR).get(),
                    params.getGradleDirectory().dir(JDKS_DIR).get(),
                    REGENERATE_FILES_ERROR);
            FileUtils.checkFilesAreTheSame(
                    params.getGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_JAR)
                            .get()
                            .getAsFile(),
                    params.getOutputGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_JAR)
                            .get()
                            .getAsFile(),
                    REGENERATE_FILES_ERROR);
            FileUtils.checkFilesAreTheSame(
                    params.getGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_SCRIPT)
                            .get()
                            .getAsFile(),
                    params.getOutputGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_SCRIPT)
                            .get()
                            .getAsFile(),
                    REGENERATE_FILES_ERROR);
        }
    }

    private static void addCerts(Directory gradleDirectory, Map<String, String> caCerts) {
        try {
            File certsDir = gradleDirectory.file(CERTS_DIR).getAsFile();
            Files.createDirectories(certsDir.toPath());
            caCerts.forEach((alias, content) -> {
                try {
                    File certFile = new File(certsDir, String.format("%s.serial-number", alias));
                    writeToFile(certFile.toPath(), CaResources.getSerialNumber(content));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addGradleJdkSetupScriptTo(Directory gradleDirectory) {
        try {
            File gradleJdksSetupScript =
                    gradleDirectory.file(GRADLE_JDKS_SETUP_SCRIPT).getAsFile();
            getResourceStream(gradleJdksSetupScript, GRADLE_JDKS_SETUP_SCRIPT);
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(gradleJdksSetupScript.toPath());
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(gradleJdksSetupScript.toPath(), permissions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addGradleJdkJarTo(Directory gradleDirectory) {
        getResourceStream(gradleDirectory.file(GRADLE_JDKS_SETUP_JAR).getAsFile(), GRADLE_JDKS_SETUP_JAR);
    }

    private static void getResourceStream(File outputFile, String resource) {
        try (InputStream inputStream =
                GradleJdkConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }

            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                inputStream.transferTo(outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createJdkFiles(
            Directory gradleDirectory, JavaLanguageVersion javaVersion, JdkDistributionConfig jdkDistribution) {
        try {
            Path outputDir = gradleDirectory
                    .dir(JDKS_DIR)
                    .getAsFile()
                    .toPath()
                    .resolve(javaVersion.toString())
                    .resolve(jdkDistribution.getOs().get().uiName())
                    .resolve(jdkDistribution.getArch().get().uiName());
            Files.createDirectories(outputDir);
            Path downloadUrlPath = outputDir.resolve(DOWNLOAD_URL);
            writeToFile(downloadUrlPath, jdkDistribution.getDownloadUrl().get());
            Path localPath = outputDir.resolve(LOCAL_PATH);
            writeToFile(localPath, jdkDistribution.getLocalPath().get());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeToFile(Path pathFile, String content) throws IOException {
        if (!Files.exists(pathFile)) {
            Files.createFile(pathFile);
        }
        String contentWithLineEnding = content + "\n";
        Files.write(pathFile, contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
    }
}
