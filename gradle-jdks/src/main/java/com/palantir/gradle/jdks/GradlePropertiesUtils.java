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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utility for ensuring required key=value pairs exist in a list of properties file lines. */
final class GradlePropertiesUtils {

    /**
     * Ensures all entries in {@code requiredProperties} are present in {@code lines}, updating existing values
     * in-place and appending missing ones. Lines that are comments, blank, or not key=value pairs are left untouched.
     */
    static void ensureProperties(List<String> lines, Map<String, String> requiredProperties) {
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
