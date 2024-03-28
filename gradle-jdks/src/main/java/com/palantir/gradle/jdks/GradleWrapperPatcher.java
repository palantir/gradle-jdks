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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

@AutoParallelizable
public abstract class GradleWrapperPatcher {

    private static final Logger log = Logging.getLogger(GradleWrapperPatcher.class);
    private static final String ENABLE_GRADLE_JDK_SETUP = "gradle.jdk.setup.enabled";
    private static final String GRADLEW_PATCH = "gradlew-patch";
    private static final String GRADLEW_UNIX_SCRIPT = "gradlew";
    private static final String COMMENT_BLOCK = "###";
    private static final String SHEBANG = "#!";

    // DO NOT CHANGE the header and the footer, they are used to identify the patch block
    private static final String GRADLEW_PATCH_HEADER = "# >>> Gradle JDK setup >>>";
    private static final String GRADLEW_PATCH_FOOTER = "# <<< Gradle JDK setup <<<";

    interface Params {
        @Inject
        ProjectLayout getProjectLayout();
    }

    public abstract static class GradleWrapperPatcherTask extends GradleWrapperPatcherTaskImpl {}

    static void action(Params params) {
        if (!isGradleJdkSetupEnabled(params.getProjectLayout())) {
            return;
        }
        log.lifecycle("Gradle JDK setup is enabled, patching the gradle wrapper files");
        Path gradlewFile = params.getProjectLayout()
                .files(GRADLEW_UNIX_SCRIPT)
                .getSingleFile()
                .toPath();
        patchGradlewContent(gradlewFile);
    }

    private static Properties loadGradleProperties(File gradleWrapperPropsFile) {
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(gradleWrapperPropsFile)) {
            properties.load(inputStream);
            return properties;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to find gradle.properties file", e);
        } catch (IOException e) {
            throw new RuntimeException("Could not read gradle.properties file", e);
        }
    }

    private static void write(Path destPath, String content) {
        try {
            Files.write(destPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write file", e);
        }
    }

    private static boolean isGradleJdkSetupEnabled(ProjectLayout projectLayout) {
        FileCollection gradleProperties = projectLayout.files("gradle.properties");
        if (gradleProperties.isEmpty() || !gradleProperties.getSingleFile().exists()) {
            return false;
        }
        Properties properties = loadGradleProperties(gradleProperties.getSingleFile());
        return Optional.ofNullable(properties.getProperty(ENABLE_GRADLE_JDK_SETUP))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private static void patchGradlewContent(Path gradlewFile) {
        List<String> linesNoPatch = getLinesWithoutPatch(gradlewFile);
        write(gradlewFile, getNewGradlewWithPatchContent(linesNoPatch));
    }

    private static List<String> getLinesWithoutPatch(Path gradlewFile) {
        List<String> initialLines = readAllLines(gradlewFile);
        OptionalInt startIndex = IntStream.range(0, initialLines.size())
                .filter(i -> initialLines.get(i).equals(GRADLEW_PATCH_HEADER))
                .findFirst();
        if (startIndex.isEmpty()) {
            return initialLines;
        }
        OptionalInt endIndex = IntStream.range(startIndex.getAsInt(), initialLines.size())
                .filter(i -> initialLines.get(i).equals(GRADLEW_PATCH_FOOTER))
                .findFirst();
        if (endIndex.isEmpty()) {
            throw new RuntimeException(
                    String.format("Invalid gradle JDK patch, missing the closing footer %s", GRADLEW_PATCH_FOOTER));
        }
        List<String> linesNoPatch = initialLines.subList(0, startIndex.getAsInt());
        if (endIndex.getAsInt() + 1 < initialLines.size()) {
            linesNoPatch.addAll(initialLines.subList(endIndex.getAsInt() + 1, initialLines.size()));
        }
        return linesNoPatch;
    }

    private static String getNewGradlewWithPatchContent(List<String> initialLines) {
        int insertIndex = getInsertLineIndex(initialLines);
        List<String> gradlewPatchLines = getGradlewPatch();
        List<String> newLines = new ArrayList<>(initialLines.size() + gradlewPatchLines.size());
        newLines.addAll(initialLines.subList(0, insertIndex));
        newLines.addAll(gradlewPatchLines);
        newLines.addAll(initialLines.subList(insertIndex, initialLines.size()));
        return newLines.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * gradlew contains a comment block that explains how it works. We are trying to add the patch block after it.
     * The fallback is adding the patch block directly after the shebang line.
     */
    private static int getInsertLineIndex(List<String> lines) {
        // first try to find the line that contains the comment block
        List<Integer> explanationBlock = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith(COMMENT_BLOCK))
                .limit(2)
                .boxed()
                .collect(Collectors.toList());
        if (explanationBlock.size() == 2 && explanationBlock.get(0) < explanationBlock.get(1)) {
            // the lines will be inserted after the first comment block ends.
            return explanationBlock.get(1) + 1;
        }
        int shebangLine = lines.indexOf(SHEBANG);
        if (shebangLine != -1) {
            // fallback: insert after the shebang
            return shebangLine + 1;
        }
        throw new RuntimeException("Unable to find where to patch the gradlew file, aborting...");
    }

    private static List<String> readAllLines(Path filePath) {
        try {
            return Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the gradlew patch file", e);
        }
    }

    private static List<String> getGradlewPatch() {
        try (InputStream inputStream =
                GradleWrapperPatcher.class.getClassLoader().getResourceAsStream(GRADLEW_PATCH)) {
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
