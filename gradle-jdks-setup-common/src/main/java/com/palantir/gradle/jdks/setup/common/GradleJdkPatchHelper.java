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

package com.palantir.gradle.jdks.setup.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class GradleJdkPatchHelper {

    // DO NOT CHANGE the header and the footer, they are used to identify the patch block
    public static final String PATCH_HEADER = "# >>> Gradle JDK setup >>>";
    public static final String PATCH_FOOTER = "# <<< Gradle JDK setup <<<";

    public static final class PatchLineNumbers {
        private final Integer startIndex;
        private final Integer endIndex;

        public PatchLineNumbers(Integer startIndex, Integer endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public static PatchLineNumbers of(Integer startIndex, Integer endIndex) {
            return new PatchLineNumbers(startIndex, endIndex);
        }

        public Integer getStartIndex() {
            return startIndex;
        }

        public Integer getEndIndex() {
            return endIndex;
        }
    }

    public static List<String> getLinesWithoutPatch(List<String> initialLines) {
        Optional<PatchLineNumbers> patchLineRange = getPatchLineNumbers(initialLines);
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

    public static void maybeRemovePatch(Path filePath) {
        try {
            List<String> initialLines = Files.readAllLines(filePath);
            List<String> linesWithoutPatch = getLinesWithoutPatch(initialLines);
            if (!linesWithoutPatch.equals(initialLines)) {
                Files.write(
                        filePath,
                        String.join("\n", linesWithoutPatch).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read file %s", filePath), e);
        }
    }

    public static byte[] getContentWithPatch(List<String> initialLines, List<String> patchLines, int insertIndex) {
        List<String> newLines = new ArrayList<>(initialLines.size() + patchLines.size());
        newLines.addAll(initialLines.subList(0, insertIndex));
        newLines.addAll(patchLines);
        newLines.addAll(initialLines.subList(insertIndex, initialLines.size()));
        return newLines.stream()
                .collect(Collectors.joining(System.lineSeparator()))
                .getBytes(StandardCharsets.UTF_8);
    }

    public static Optional<PatchLineNumbers> getPatchLineNumbers(List<String> content) {
        IntStream startPatchIdxStream = IntStream.range(0, content.size())
                .filter(i -> content.get(i).endsWith(PATCH_HEADER))
                .limit(2);
        List<Integer> startPatchIndexes = startPatchIdxStream.boxed().collect(Collectors.toList());
        checkState(
                startPatchIndexes.size() <= 1,
                String.format(
                        "Invalid gradle JDK patch, expected at most 1 Gradle JDK setup header, but got %s",
                        startPatchIndexes.size()));
        Optional<Integer> startIndex = startPatchIndexes.stream().findFirst();
        if (startPatchIndexes.isEmpty()) {
            return Optional.empty();
        }
        IntStream endPatchIdxStream = IntStream.range(startIndex.get(), content.size())
                .filter(i -> content.get(i).endsWith(PATCH_FOOTER))
                .limit(2);
        List<Integer> endPatchIndexes = endPatchIdxStream.boxed().collect(Collectors.toList());
        checkState(
                endPatchIndexes.size() <= 1,
                String.format(
                        "Invalid gradle JDK patch, expected at most 1 Gradle JDK setup footer, but got %s",
                        endPatchIndexes.size()));
        Optional<Integer> endIndex = endPatchIndexes.stream().findFirst();
        if (endIndex.isEmpty()) {
            throw new RuntimeException(
                    String.format("Invalid gradle JDK patch, missing the closing footer %s", PATCH_FOOTER));
        }
        return Optional.of(PatchLineNumbers.of(startIndex.get(), endIndex.get()));
    }

    private static void checkState(boolean result, String errorMessage) {
        if (!result) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private GradleJdkPatchHelper() {}
}
