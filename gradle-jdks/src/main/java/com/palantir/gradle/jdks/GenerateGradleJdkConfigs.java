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
import groovyjarjarpicocli.CommandLine.Option;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public class GenerateGradleJdkConfigs {

    private static final Logger log = Logging.getLogger(GenerateGradleJdkConfigs.class);

    interface JdkDistributionConfig {

        @Input
        Property<String> getDownloadUrl();

        @Input
        Property<String> getLocalPath();

        @Input
        Property<Os> getOs();

        @Input
        Property<Arch> getArch();
    }

    interface Params {

        @Nested
        MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

        @Input
        Property<JavaLanguageVersion> getDaemonJavaVersion();

        @Option(names = "fix", description = "Fixes the gradle jdk files")
        @Input
        Property<Boolean> getFix();

        @OutputFile
        RegularFileProperty getDaemonJdkFile();

        @Optional
        @Internal
        DirectoryProperty getGradleJdkDirectory();

        @OutputDirectory
        DirectoryProperty getOutputGradleJdkDirectory();
    }

    public abstract static class GenerateGradleJdkConfigsTask extends GenerateGradleJdkConfigsTaskImpl {

        public GenerateGradleJdkConfigsTask() {
            this.getFix().convention(false);
        }
    }

    static void action(Params params) {
        params.getJavaVersionToJdkDistros().get().forEach((javaVersion, jdkDistros) -> {
            jdkDistros.forEach(jdkDistro -> createJdkFiles(params, javaVersion, jdkDistro));
        });

        try {
            writeToFile(
                    params.getDaemonJdkFile().get().getAsFile().toPath(),
                    params.getDaemonJavaVersion().get().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!params.getGradleJdkDirectory()
                .get()
                .getAsFile()
                .equals(params.getOutputGradleJdkDirectory().get().getAsFile())) {
            checkDirectoriesAreTheSame(
                    params.getGradleJdkDirectory().get().getAsFile().toPath(),
                    params.getOutputGradleJdkDirectory().get().getAsFile().toPath());
        }
    }

    private static void checkDirectoriesAreTheSame(Path originalDir, Path outputDir) {
        try {
            Files.walkFileTree(originalDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileVisitResult result = super.visitFile(file, attrs);

                    // get the relative file name from path "one"
                    Path relativize = originalDir.relativize(file);
                    // construct the path for the counterpart file in "other"
                    Path fileInOther = outputDir.resolve(relativize);
                    log.debug("=== comparing: {} to {}", file, fileInOther);

                    byte[] otherBytes = Files.readAllBytes(fileInOther);
                    byte[] theseBytes = Files.readAllBytes(file);
                    if (!Arrays.equals(otherBytes, theseBytes)) {
                        throw new ExceptionWithSuggestion(
                                "The gradle configuration files in `gradle/` are out of date",
                                "./gradlew updateGradleJdks --fix");
                    }
                    return result;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createJdkFiles(
            Params params, JavaLanguageVersion javaVersion, JdkDistributionConfig jdkDistribution) {
        try {
            Path outputDir = params.getGradleJdkDirectory()
                    .get()
                    .getAsFile()
                    .toPath()
                    .resolve(javaVersion.toString())
                    .resolve(jdkDistribution.getOs().get().uiName())
                    .resolve(jdkDistribution.getArch().get().uiName());
            Files.createDirectories(outputDir);
            Path downloadUrlPath = outputDir.resolve("download-url");
            writeToFile(downloadUrlPath, jdkDistribution.getDownloadUrl().get());
            Path localPath = outputDir.resolve("local-path");
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
