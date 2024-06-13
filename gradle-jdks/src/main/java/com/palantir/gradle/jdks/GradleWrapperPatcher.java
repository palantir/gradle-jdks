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

import com.google.common.base.Preconditions;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.jdks.GradleJdkPatchHelper.PatchLineNumbers;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public abstract class GradleWrapperPatcher {

    private static final Logger log = Logging.getLogger(GradleWrapperPatcher.class);
    private static final String GRADLEW_PATCH = "gradlew-patch.sh";
    private static final String COMMENT_BLOCK = "###";
    private static final String SHEBANG = "#!";

    interface Params {

        @InputFile
        RegularFileProperty getOriginalGradlewScript();

        @Input
        Property<Boolean> getGenerate();

        @OutputFile
        RegularFileProperty getPatchedGradlewScript();

        @Internal
        RegularFileProperty getBuildDir();
    }

    public abstract static class GradleWrapperPatcherTask extends GradleWrapperPatcherTaskImpl {

        public GradleWrapperPatcherTask() {
            getGenerate().convention(false);
        }
    }

    static void action(Params params) {
        if (params.getGenerate().get()) {
            log.lifecycle("Gradle JDK setup is enabled, patching the gradle wrapper files");
            patchGradlewContent(params.getOriginalGradlewScript().getAsFile().get(), params.getPatchedGradlewScript());
        } else {
            checkGradlewContainsPatch(params.getOriginalGradlewScript().get().getAsFile());
        }
    }

    private static void checkGradlewContainsPatch(File gradlewScript) {
        List<String> scriptPatchLines = getPatchedLines(gradlewScript);
        List<String> originalPatchLines = getGradlewPatch();
        if (!scriptPatchLines.equals(originalPatchLines)) {
            throw new ExceptionWithSuggestion(
                    "Gradle Wrapper script is out of date, please run `./gradlew wrapperJdkPatcher`",
                    "./gradlew wrapperJdkPatcher");
        }
    }

    private static void patchGradlewContent(File originalGradlewScript, RegularFileProperty patchedGradlewScript) {
        List<String> linesNoPatch = getLinesWithoutPatch(originalGradlewScript);
        write(patchedGradlewScript.getAsFile().get().toPath(), getNewGradlewWithPatchContent(linesNoPatch));
    }

    private static void write(Path destPath, String content) {
        try {
            Files.write(destPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write file", e);
        }
    }

    private static List<String> getLinesWithoutPatch(File gradlewFile) {
        List<String> initialLines = readAllLines(gradlewFile.toPath());
        Optional<PatchLineNumbers> patchLineRange = GradleJdkPatchHelper.getPatchLineNumbers(initialLines);
        if (patchLineRange.isEmpty()) {
            return initialLines;
        }
        int startIndex = patchLineRange.get().getStartIndex();
        int endIndex = patchLineRange.get().getEndIndex();
        List<String> linesNoPatch = initialLines.subList(0, startIndex);
        if (endIndex + 1 < initialLines.size()) {
            linesNoPatch.addAll(initialLines.subList(endIndex + 1, initialLines.size()));
        }
        return linesNoPatch;
    }

    private static List<String> getPatchedLines(File gradlewFile) {
        List<String> initialLines = readAllLines(gradlewFile.toPath());
        return GradleJdkPatchHelper.getPatchLineNumbers(initialLines)
                .map(integerIntegerPair ->
                        initialLines.subList(integerIntegerPair.getStartIndex(), integerIntegerPair.getEndIndex() + 1))
                .orElseGet(List::of);
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
            Preconditions.checkArgument(inputStream != null);
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the gradlew patch file", e);
        }
    }
}
