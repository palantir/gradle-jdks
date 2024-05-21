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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.jdks.setup.CaResources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public abstract class GradleJdkConfigs {

    private static final Logger log = Logging.getLogger(GradleJdkConfigs.class);
    private static final String TASK_NAME = "generateGradleJdkConfigs";
    private static final String JDKS_DIR = "jdks";
    private static final String CERTS_DIR = "certs";
    private static final String DOWNLOAD_URL = "download-url";
    private static final String LOCAL_PATH = "local-path";
    private static final String GRADLE_JDKS_SETUP_JAR = "gradle-jdks-setup.jar";
    private static final String GRADLE_JDKS_SETUP_SCRIPT = "gradle-jdks-setup.sh";
    private static final String GRADLE_DAEMON_JDK_VERSION = "gradle-daemon-jdk-version";

    interface JdkDistributionConfig {

        @Input
        Property<String> getDistributionName();

        @Input
        Property<String> getVersion();

        @Input
        Property<Os> getOs();

        @Input
        Property<Arch> getArch();

        @Input
        Property<String> getConsistentHash();
    }

    interface Params {

        @Internal
        Property<JdkDistributionsService> getJdkDistributions();

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
            jdkDistros.forEach(jdkDistro -> createJdkFiles(
                    params.getJdkDistributions().get(),
                    params.getOutputGradleDirectory().get(),
                    javaVersion,
                    jdkDistro));
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
            checkDirectoriesAreTheSame(
                    params.getOutputGradleDirectory().dir(JDKS_DIR).get(),
                    params.getGradleDirectory().dir(JDKS_DIR).get());
            checkFilesAreTheSame(
                    params.getGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_JAR)
                            .get()
                            .getAsFile(),
                    params.getOutputGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_JAR)
                            .get()
                            .getAsFile());
            checkFilesAreTheSame(
                    params.getGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_SCRIPT)
                            .get()
                            .getAsFile(),
                    params.getOutputGradleDirectory()
                            .file(GRADLE_JDKS_SETUP_SCRIPT)
                            .get()
                            .getAsFile());
            // TODO(crogoz): check that we already patched ./gradlew & gradle-jars

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

    @VisibleForTesting
    static void checkFilesAreTheSame(File originalPath, File outputPath) {
        try {
            byte[] originalBytes = Files.readAllBytes(originalPath.toPath());
            byte[] outputBytes = Files.readAllBytes(outputPath.toPath());
            if (!Arrays.equals(originalBytes, outputBytes)) {
                throw new ExceptionWithSuggestion(
                        String.format("The gradle file %s is out of date", outputPath),
                        String.format("./gradlew %s", TASK_NAME));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkDirectoriesAreTheSame(Directory dir1, Directory dir2) {
        try {
            Files.walkFileTree(dir1.getAsFile().toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileVisitResult result = super.visitFile(file, attrs);
                    Path relativize = dir1.getAsFile().toPath().relativize(file);
                    Path fileInOther = dir2.getAsFile().toPath().resolve(relativize);
                    byte[] otherBytes = Files.readAllBytes(fileInOther);
                    byte[] theseBytes = Files.readAllBytes(file);
                    if (!Arrays.equals(otherBytes, theseBytes)) {
                        throw new ExceptionWithSuggestion(
                                "The gradle configuration files in `gradle/jdks` are out of date",
                                String.format("./gradlew %s", TASK_NAME));
                    }
                    return result;
                }
            });
        } catch (IOException e) {
            throw new ExceptionWithSuggestion(
                    "The gradle configuration files in `gradle/jdks` are out of date",
                    String.format("./gradlew %s", TASK_NAME));
        }
    }

    private static void createJdkFiles(
            JdkDistributionsService jdkDistributionsService,
            Directory gradleDirectory,
            JavaLanguageVersion javaVersion,
            JdkDistributionConfig jdkDistribution) {
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
            JdkRelease jdkRelease = JdkRelease.builder()
                    .version(jdkDistribution.getVersion().get())
                    .os(jdkDistribution.getOs().get())
                    .arch(jdkDistribution.getArch().get())
                    .build();
            JdkDistributionName jdkDistributionName = JdkDistributionName.fromStringThrowing(
                    jdkDistribution.getDistributionName().get());
            JdkPath jdkPath = jdkDistributionsService.get(jdkDistributionName).path(jdkRelease);
            String downloadUrl = String.format(
                    "%s/%s.%s",
                    jdkDistributionsService.get(jdkDistributionName).defaultBaseUrl(),
                    jdkPath.filename(),
                    jdkPath.extension());
            String localFileName = String.format(
                    "%s-%s-%s",
                    jdkDistributionName,
                    jdkDistribution.getVersion().get(),
                    jdkDistribution.getConsistentHash().get());
            writeToFile(downloadUrlPath, downloadUrl);
            Path localPath = outputDir.resolve(LOCAL_PATH);
            writeToFile(localPath, localFileName);
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
