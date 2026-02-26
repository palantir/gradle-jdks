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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Ensures that gradle.properties contains the required toolchain properties for gradle-jdks.
 * Specifically, sets auto-detect and auto-download to false so that Gradle only uses JDKs
 * configured by the gradle-jdks plugin.
 */
public abstract class EnsureGradlePropertiesTask extends DefaultTask {

    private static final Map<String, String> REQUIRED_PROPERTIES = Map.of(
            "org.gradle.java.installations.auto-detect", "false",
            "org.gradle.java.installations.auto-download", "false");

    @OutputFile
    public abstract RegularFileProperty getGradlePropertiesFile();

    @TaskAction
    public final void ensureProperties() {
        Path gradleProperties = getGradlePropertiesFile().get().getAsFile().toPath();
        List<String> lines = readLines(gradleProperties);

        Map<String, Boolean> found = new LinkedHashMap<>();
        REQUIRED_PROPERTIES.keySet().forEach(key -> found.put(key, false));

        for (int i = 0; i < lines.size(); i++) {
            String lineKey = extractPropertyKey(lines.get(i));
            if (lineKey != null && REQUIRED_PROPERTIES.containsKey(lineKey)) {
                lines.set(i, lineKey + "=" + REQUIRED_PROPERTIES.get(lineKey));
                found.put(lineKey, true);
            }
        }

        REQUIRED_PROPERTIES.forEach((key, value) -> {
            if (!found.get(key)) {
                lines.add(key + "=" + value);
            }
        });

        writeLines(gradleProperties, lines);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.exists(path)
                    ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.ISO_8859_1))
                    : new ArrayList<>();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read gradle.properties", e);
        }
    }

    private static void writeLines(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write gradle.properties", e);
        }
    }

    /**
     * Extracts the property key from a line, or returns null if the line is a comment, blank, or not
     * a key=value pair.
     */
    private static String extractPropertyKey(String line) {
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
}
