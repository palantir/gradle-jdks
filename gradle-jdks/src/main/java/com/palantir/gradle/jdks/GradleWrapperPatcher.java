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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    private static final String COMMENT_BLOCK = "###";
    private static final String SHEBANG = "#!";

    interface Params {

        @InputFile
        RegularFileProperty getOriginalGradlewScript();

        @InputFile
        RegularFileProperty getOriginalBatchScript();

        @Input
        Property<Boolean> getGenerate();

        @OutputFile
        RegularFileProperty getPatchedGradlewScript();

        @OutputFile
        RegularFileProperty getPatchedBatchScript();

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
            patchBatchContent(params.getPatchedBatchScript().getAsFile().get(), params.getPatchedBatchScript());
        } else {
            checkContainsPatch(params.getOriginalGradlewScript().get().getAsFile(), "gradlew-patch.sh");
            checkContainsPatch(params.getOriginalBatchScript().get().getAsFile(), "batch-patch.bat");
        }
    }

    private static void checkContainsPatch(File gradlewScript, String patchResource) {
        List<String> scriptPatchLines = getPatchedLines(gradlewScript);
        List<String> originalPatchLines = getPatchLines(patchResource);
        if (!scriptPatchLines.equals(originalPatchLines)) {
            throw new ExceptionWithSuggestion(
                    "Gradle Wrapper script is out of date, please run `./gradlew(.bat) wrapperJdkPatcher`",
                    "./gradlew wrapperJdkPatcher");
        }
    }

    private static void patchBatchContent(File originalGradlewScript, RegularFileProperty patchedGradlewScript) {
        List<String> initialLines = readAllLines(originalGradlewScript.toPath());
        List<String> linesNoPatch = GradleJdkPatchHelper.getLinesWithoutPatch(initialLines);
        int insertIndex = getBatchInsertLineIndex(initialLines);
        List<String> patchLines = getPatchLines("batch-patch.bat");
        write(
                patchedGradlewScript.getAsFile().get().toPath(),
                GradleJdkPatchHelper.getContentWithPatch(linesNoPatch, patchLines, insertIndex));
    }

    private static void patchGradlewContent(File originalGradlewScript, RegularFileProperty patchedGradlewScript) {
        List<String> initialLines = readAllLines(originalGradlewScript.toPath());
        List<String> linesNoPatch = GradleJdkPatchHelper.getLinesWithoutPatch(initialLines);
        List<String> patchLines = getPatchLines("gradlew-patch.sh");
        int insertIndex = getGradlewInsertLineIndex(initialLines);
        write(
                patchedGradlewScript.getAsFile().get().toPath(),
                GradleJdkPatchHelper.getContentWithPatch(linesNoPatch, patchLines, insertIndex));
    }

    private static void write(Path destPath, byte[] content) {
        try {
            Files.write(destPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write file", e);
        }
    }

    private static List<String> getPatchedLines(File gradlewFile) {
        List<String> initialLines = readAllLines(gradlewFile.toPath());
        return GradleJdkPatchHelper.getPatchLineNumbers(initialLines)
                .map(integerIntegerPair ->
                        initialLines.subList(integerIntegerPair.getStartIndex(), integerIntegerPair.getEndIndex() + 1))
                .orElseGet(List::of);
    }

    private static int getBatchInsertLineIndex(List<String> lines) {
        List<Integer> explanationBlock = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith(":execute"))
                .limit(1)
                .boxed()
                .collect(Collectors.toList());
        if (explanationBlock.isEmpty()) {
            throw new RuntimeException("Unable to find where to patch the gradlew.bat file, aborting...");
        }
        return explanationBlock.get(0);
    }

    /**
     * gradlew contains a comment block that explains how it works. We are trying to add the patch block after it.
     * The fallback is adding the patch block directly after the shebang line.
     */
    private static int getGradlewInsertLineIndex(List<String> lines) {
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

    private static List<String> getPatchLines(String resource) {
        try (InputStream inputStream =
                GradleWrapperPatcher.class.getClassLoader().getResourceAsStream(resource)) {
            Preconditions.checkArgument(inputStream != null);
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read the %s patch file", resource), e);
        }
    }
}
