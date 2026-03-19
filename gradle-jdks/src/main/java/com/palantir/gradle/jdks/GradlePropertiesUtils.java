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

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/** Utility for ensuring required gradle-jdks toolchain properties exist in a properties file. */
final class GradlePropertiesUtils {

    private static final Map<String, String> REQUIRED_PROPERTIES = Map.of(
            "org.gradle.java.installations.auto-detect", "false",
            "org.gradle.java.installations.auto-download", "false");

    /**
     * Ensures that {@code auto-detect=false} and {@code auto-download=false} are present in the given
     * properties file content, updating existing values in-place and appending missing ones. Comments,
     * blank lines, and non-key=value lines are preserved. Trailing newlines are preserved.
     */
    static String ensureProperties(String content) {
        List<String> lines = Splitter.on('\n').splitToList(content);

        // Replace existing properties with required values
        List<String> updatedLines = lines.stream()
                .map(line -> extractPropertyKey(line)
                        .filter(REQUIRED_PROPERTIES::containsKey)
                        .map(key -> key + "=" + REQUIRED_PROPERTIES.get(key))
                        .orElse(line))
                .toList();

        // Append any properties that aren't already present in the file
        List<String> missingEntries = EntryStream.of(REQUIRED_PROPERTIES)
                .filterKeys(key -> updatedLines.stream()
                        .noneMatch(line ->
                                extractPropertyKey(line).map(key::equals).orElse(false)))
                .mapKeyValue((key, value) -> key + "=" + value)
                .toList();

        // If the input ended with a newline, Splitter produces a trailing empty element.
        // Drop it before appending so we don't get a blank line before the new entries.
        boolean endsWithNewline =
                !updatedLines.isEmpty() && updatedLines.get(updatedLines.size() - 1).isEmpty();
        StreamEx<String> base = endsWithNewline
                ? StreamEx.of(updatedLines.subList(0, updatedLines.size() - 1))
                : StreamEx.of(updatedLines);

        String result = base.append(missingEntries).joining("\n");

        boolean appendedEntries = !missingEntries.isEmpty();
        boolean shouldEndWithNewline = endsWithNewline || appendedEntries;
        if (shouldEndWithNewline && !result.endsWith("\n")) {
            result += "\n";
        }
        return result;
    }

    /**
     * Extracts the property key from a line, returning empty if the line is a comment, blank, or not
     * a key=value pair.
     */
    static Optional<String> extractPropertyKey(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return Optional.empty();
        }
        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(trimmed.substring(0, equalsIndex).trim());
    }

    private GradlePropertiesUtils() {}
}
