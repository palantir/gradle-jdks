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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        boolean endsWithNewline = content.endsWith("\n");
        List<String> lines = Splitter.on('\n').splitToList(content);

        // Splitter produces a trailing empty element when the string ends with the delimiter
        if (endsWithNewline && !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines = lines.subList(0, lines.size() - 1);
        }

        // Replace existing properties with required values
        List<String> updatedLines = lines.stream()
                .map(line -> {
                    String lineKey = extractPropertyKey(line);
                    if (lineKey != null && REQUIRED_PROPERTIES.containsKey(lineKey)) {
                        return lineKey + "=" + REQUIRED_PROPERTIES.get(lineKey);
                    }
                    return line;
                })
                .collect(Collectors.toList());

        // Append any properties that aren't already present in the file
        List<String> missingEntries = REQUIRED_PROPERTIES.entrySet().stream()
                .filter(entry ->
                        updatedLines.stream().noneMatch(line -> entry.getKey().equals(extractPropertyKey(line))))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();

        String result =
                Stream.concat(updatedLines.stream(), missingEntries.stream()).collect(Collectors.joining("\n"));
        if (endsWithNewline || !missingEntries.isEmpty()) {
            result += "\n";
        }
        return result;
    }

    /**
     * Extracts the property key from a line, or returns null if the line is a comment, blank, or not
     * a key=value pair.
     */
    static String extractPropertyKey(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null;
        }
        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex < 0) {
            return null;
        }
        return trimmed.substring(0, equalsIndex).trim();
    }

    private GradlePropertiesUtils() {}
}
