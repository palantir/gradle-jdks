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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public abstract class GradleWrapperPatcher {

    private static final String ENABLE_GRADLE_JDK_SETUP = "gradle.jdk.setup.enabled";
    private static final Logger log = Logging.getLogger(GradleWrapperPatcher.class);

    interface Params {

        @OutputFile
        RegularFileProperty getGradlewUnixScriptFile();

        @InputFile
        RegularFileProperty getGradlePropsFile();
    }

    public abstract static class GradleWrapperPatcherTask extends GradleWrapperPatcherTaskImpl {}

    static void action(Params params) {
        Properties properties = loadProperties(params.getGradlePropsFile().get().getAsFile());
        if (!isGradleJdkSetupEnabled(properties)) {
            return;
        }
        log.lifecycle("Gradle JDK setup is enabled, patching the gradle wrapper files");
        // make sure all changes are transactional!
        Path gradlewFile = params.getGradlewUnixScriptFile().get().getAsFile().toPath();
        maybeGetNewGradlewContent(gradlewFile).ifPresent(newGradlewContent -> write(gradlewFile, newGradlewContent));
    }

    private static Properties loadProperties(File gradleWrapperPropsFile) {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(gradleWrapperPropsFile)) {
            properties.load(inputStream);
            return properties;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to find gradle-wrapper.properties file", e);
        } catch (IOException e) {
            throw new RuntimeException("Could not read gradle-wrapper.properties file", e);
        }
    }

    private static void write(Path destPath, String content) {
        try {
            Files.write(destPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write file", e);
        }
    }

    private static boolean isGradleJdkSetupEnabled(Properties properties) {
        return Optional.ofNullable(properties.getProperty(ENABLE_GRADLE_JDK_SETUP))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private static Optional<String> maybeGetNewGradlewContent(Path gradlewFile) {
        try {
            List<String> lines = Files.readAllLines(gradlewFile);
            if (patchAlreadyPresent(lines)) {
                return Optional.empty();
            }
            int insertIndex = getLineIndex(lines) + 1;
            return Optional.of(getGradlewWithPatch(insertIndex, lines));
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the gradlew script file", e);
        }
    }

    private static boolean patchAlreadyPresent(List<String> lines) {
        return lines.stream().anyMatch(line -> line.contains("gradle-jdks-setup.sh"));
    }

    private static int getLineIndex(List<String> lines) {
        // first try to find the line that contains the comment block
        List<Integer> explanationBlock = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith("###"))
                .limit(2)
                .boxed()
                .collect(Collectors.toList());
        if (explanationBlock.size() == 2 && explanationBlock.get(0) < explanationBlock.get(1)) {
            return explanationBlock.get(1);
        }
        // if the comment block is not found, try to find the shebang line
        int shebangLine = lines.indexOf("#!");
        if (shebangLine != -1) {
            return shebangLine;
        }
        throw new RuntimeException("Unable to find where to patch the gradlew file, aborting...");
    }

    private static List<String> getGradlewPatchLines() {
        return List.of(
                String.format("# Setting up the Gradle JDK because %s is enabled", ENABLE_GRADLE_JDK_SETUP),
                "echo Setting up the Gradle JDK is starting",
                "source gradle/gradle-jdks-setup.sh",
                "echo $?",
                "echo Setting up the Gradle JDK is complete");
    }

    private static String getGradlewWithPatch(int insertIndex, List<String> initialLines) {
        List<String> gradlewPatchLines = getGradlewPatchLines();
        List<String> newLines = new ArrayList<>(initialLines.size() + gradlewPatchLines.size());
        newLines.addAll(initialLines.subList(0, insertIndex));
        newLines.addAll(gradlewPatchLines);
        if (insertIndex + 1 >= initialLines.size()) {
            throw new RuntimeException(String.format(
                    "Unexpected index %s to insert the patched gradlew size: %s, aborting",
                    insertIndex, initialLines.size()));
        }
        newLines.addAll(initialLines.subList(insertIndex + 1, initialLines.size()));
        return newLines.stream().collect(Collectors.joining("\n"));
    }
}
