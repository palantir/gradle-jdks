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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility for ensuring required key=value pairs exist in a properties file. */
final class GradlePropertiesUtils {

    /**
     * Ensures all entries in {@code requiredProperties} are present in the given properties file content,
     * updating existing values in-place and appending missing ones. Comments, blank lines, and non-key=value
     * lines are preserved. Trailing newlines are preserved.
     */
    static String ensureProperties(String content, Map<String, String> requiredProperties) {
        boolean endsWithNewline = content.endsWith("\n");
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));

        // split("...", -1) produces a trailing empty element when the string ends with the delimiter
        if (endsWithNewline && !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        Map<String, Boolean> found = new LinkedHashMap<>();
        requiredProperties.keySet().forEach(key -> found.put(key, false));

        for (int i = 0; i < lines.size(); i++) {
            String lineKey = extractPropertyKey(lines.get(i));
            if (lineKey != null && requiredProperties.containsKey(lineKey)) {
                lines.set(i, lineKey + "=" + requiredProperties.get(lineKey));
                found.put(lineKey, true);
            }
        }

        requiredProperties.forEach((key, value) -> {
            if (!found.get(key)) {
                lines.add(key + "=" + value);
            }
        });

        boolean appendedNewLines = found.containsValue(false);
        String result = lines.stream().collect(Collectors.joining("\n"));
        if (endsWithNewline || appendedNewLines) {
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
