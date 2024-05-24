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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
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

    // DO NOT CHANGE the header and the footer, they are used to identify the patch block
    private static final String GRADLEW_PATCH_HEADER = "# >>> Gradle JDK setup >>>";
    private static final String GRADLEW_PATCH_FOOTER = "# <<< Gradle JDK setup <<<";

    interface Params {

        @InputFile
        RegularFileProperty getOriginalGradlewScript();

        @InputFile
        RegularFileProperty getOriginalGradleWrapperJar();

        @InputFile
        RegularFileProperty getGradleJdksSetupJar();

        @Input
        Property<Boolean> getGenerate();

        @OutputFile
        RegularFileProperty getPatchedGradlewScript();

        @OutputFile
        RegularFileProperty getPatchedGradleWrapperJar();

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
            patchGradlewJar(
                    params.getBuildDir().get().getAsFile(),
                    params.getOriginalGradleWrapperJar().get().getAsFile(),
                    params.getPatchedGradleWrapperJar().get().getAsFile(),
                    params.getGradleJdksSetupJar().get().getAsFile());
        } else {
            checkGradlewContainsPatch(params.getOriginalGradlewScript().get().getAsFile());
            checkGradleJarContainsPatch(
                    params.getBuildDir().get().getAsFile(),
                    params.getOriginalGradleWrapperJar().get().getAsFile(),
                    params.getGradleJdksSetupJar().get().getAsFile());
        }
    }

    private static void checkGradlewContainsPatch(File gradlewScript) {
        List<String> scriptPatchLines = getPatchedLines(gradlewScript);
        List<String> originalPatchLines = getGradlewPatch();
        if (!scriptPatchLines.equals(originalPatchLines)) {
            throw new ExceptionWithSuggestion(
                    String.format(
                            "Gradle Wrapper script is out of date, please run `./gradlew wrapperJdkPatcher`"
                                    + " to update the JDKs scriptPatchLines %s originalPatchLines %s",
                            scriptPatchLines, originalPatchLines),
                    "./gradlew wrapperJdkPatcher");
        }
    }

    private static void checkGradleJarContainsPatch(File buildDir, File gradleWrapperJar, File gradleJdksSetupJar) {
        try {
            Path gradleWrapperExtractedDir = buildDir.toPath().resolve("check-gradle-wrapper-patcher/gradle-wrapper");
            Files.createDirectories(gradleWrapperExtractedDir);
            JarResources.extractJar(gradleWrapperJar, gradleWrapperExtractedDir);
            Path gradleWrapperMain =
                    OriginalGradleWrapperMainCreator.getGradleWrapperClassPath(gradleWrapperExtractedDir);
            OriginalGradleWrapperMainCreator.create(gradleWrapperExtractedDir);
            Path gradleJdksExtracted = buildDir.toPath().resolve("check-gradle-wrapper-patcher/gradle-jdks");
            Files.createDirectories(gradleJdksExtracted);
            JarResources.extractJar(gradleJdksSetupJar, gradleJdksExtracted);
            Path gradleWrapperMainFromSetupJar =
                    OriginalGradleWrapperMainCreator.getGradleWrapperClassPath(gradleJdksExtracted);
            FileUtils.checkFilesAreTheSame(gradleWrapperMain.toFile(), gradleWrapperMainFromSetupJar.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to check gradle wrapper jar", e);
        }
    }

    private static void patchGradlewContent(File originalGradlewScript, RegularFileProperty patchedGradlewScript) {
        List<String> linesNoPatch = getLinesWithoutPatch(originalGradlewScript);
        write(patchedGradlewScript.getAsFile().get().toPath(), getNewGradlewWithPatchContent(linesNoPatch));
    }

    private static void patchGradlewJar(
            File buildDir, File originalGradleWrapperJar, File patchedGradleWrapperJar, File gradleJdksSetupJar) {
        try {
            Path gradleWrapperExtractedDir = buildDir.toPath().resolve("gradle-wrapper-extracted");
            Files.createDirectories(gradleWrapperExtractedDir);
            JarResources.extractJar(originalGradleWrapperJar, gradleWrapperExtractedDir);
            OriginalGradleWrapperMainCreator.create(gradleWrapperExtractedDir);
            JarResources.extractJar(gradleJdksSetupJar, gradleWrapperExtractedDir);

            Path newGradleWrapperJar = buildDir.toPath().resolve(patchedGradleWrapperJar.getName());
            JarResources.createJarFromDirectory(gradleWrapperExtractedDir.toFile(), newGradleWrapperJar.toFile());
            moveFile(newGradleWrapperJar, patchedGradleWrapperJar.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to patch gradle wrapper jar", e);
        }
    }

    private static void moveFile(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
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
        Optional<Pair<Integer, Integer>> startEndPatch = getPatchLineNumbers(initialLines);
        if (startEndPatch.isEmpty()) {
            return initialLines;
        }
        int startIndex = startEndPatch.get().getLeft();
        int endIndex = startEndPatch.get().getRight();
        List<String> linesNoPatch = initialLines.subList(0, startIndex);
        if (endIndex + 1 < initialLines.size()) {
            linesNoPatch.addAll(initialLines.subList(endIndex + 1, initialLines.size()));
        }
        return linesNoPatch;
    }

    private static List<String> getPatchedLines(File gradlewFile) {
        List<String> initialLines = readAllLines(gradlewFile.toPath());
        Optional<Pair<Integer, Integer>> startEndPatch = getPatchLineNumbers(initialLines);
        if (startEndPatch.isEmpty()) {
            return List.of();
        }
        return initialLines.subList(
                startEndPatch.get().getLeft(), startEndPatch.get().getRight() + 1);
    }

    private static Optional<Pair<Integer, Integer>> getPatchLineNumbers(List<String> content) {
        OptionalInt startIndex = IntStream.range(0, content.size())
                .filter(i -> content.get(i).equals(GRADLEW_PATCH_HEADER))
                .findFirst();
        if (startIndex.isEmpty()) {
            return Optional.empty();
        }
        OptionalInt endIndex = IntStream.range(startIndex.getAsInt(), content.size())
                .filter(i -> content.get(i).equals(GRADLEW_PATCH_FOOTER))
                .findFirst();
        if (endIndex.isEmpty()) {
            throw new RuntimeException(
                    String.format("Invalid gradle JDK patch, missing the closing footer %s", GRADLEW_PATCH_FOOTER));
        }
        return Optional.of(Pair.of(startIndex.getAsInt(), endIndex.getAsInt()));
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
            throw new RuntimeException("Unable to read the gradlew patch file", e);
        }
    }
}
