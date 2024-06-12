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

package com.palantir.gradle.jdks.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GradleJdkPropertiesSetup {

    public static void main(String[] args) {
        Path projectDir = Path.of(args[0]);
        String allJdkSymlinks = parseToolchains(args[1]);
        updateGradleProperties(
                projectDir.resolve("gradle.properties"),
                Map.of(
                        // Set the custom toolchains locations
                        "org.gradle.java.installations.paths",
                        allJdkSymlinks,
                        // Disable auto-download and auto-detect of JDKs
                        "org.gradle.java.installations.auto-download",
                        "false",
                        "org.gradle.java.installations.auto-detect",
                        "false"));

        // [Intelij] Update the .idea files with the startup script
        Path targetProjectIdea = projectDir.resolve(".idea");
        List<Path> newIdeaFiles = writeIdeaFiles(targetProjectIdea);

        // [Intelij] Update .gitignore to not ignore the newly added .idea files & ignore the jdk-* symlinks
        updateIdeaGitignore(projectDir, newIdeaFiles);
    }

    private static List<Path> writeIdeaFiles(Path targetProjectIdea) {
        URL ideaConfigurations = GradleJdkPropertiesSetup.class.getClassLoader().getResource("ideaConfigurations");
        try {
            return getAndCopyJarResources(targetProjectIdea, (JarURLConnection) ideaConfigurations.openConnection());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Path> getAndCopyJarResources(Path destination, JarURLConnection jarConnection)
            throws IOException {
        List<Path> newIdeaFiles = new ArrayList<>();
        JarFile jarFile = jarConnection.getJarFile();
        for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
            JarEntry entry = it.next();
            if (entry.getName().startsWith(jarConnection.getEntryName())
                    && !entry.getName().equals(jarConnection.getEntryName())) {
                String entryName = entry.getName().replace(jarConnection.getEntryName() + "/", "");
                Path destinationFile = destination.resolve(entryName);
                if (!entry.isDirectory()) {
                    try (InputStream entryInputStream = jarFile.getInputStream(entry)) {
                        Files.copy(
                                entryInputStream, destination.resolve(entryName), StandardCopyOption.REPLACE_EXISTING);
                        newIdeaFiles.add(destinationFile);
                    }
                } else {
                    FileUtils.createDirectories(destinationFile);
                }
            }
        }
        return newIdeaFiles;
    }

    private static void updateIdeaGitignore(Path projectDir, List<Path> pathsToBeCommitted) {
        // try to add the lines after ".idea/" in .gitignore if it exists
        try {
            Path gitignoreFile = projectDir.resolve(".gitignore");
            if (!Files.exists(gitignoreFile)) {
                Files.createFile(gitignoreFile);
            }
            List<String> gitignoreLines = Files.readAllLines(gitignoreFile);
            int ideaIndex = gitignoreLines.indexOf(".idea/");
            if (ideaIndex != -1) {
                gitignoreLines.remove(ideaIndex);
                gitignoreLines.add(ideaIndex, ".idea/*");
            }
            List<String> gitignorePatch = getGitignorePatch(projectDir, pathsToBeCommitted);
            List<String> linesNoPatch = GradleJdkPatchHelper.getLinesWithoutPatch(gitignoreLines);
            writeContentWithPatch(gitignoreFile, linesNoPatch, gitignorePatch);

        } catch (IOException e) {
            throw new RuntimeException("Unable to update the .gitignore file.", e);
        }
    }

    public static void writeContentWithPatch(Path file, List<String> linesNoPatch, List<String> patchLines) {
        try {
            Files.write(
                    file,
                    GradleJdkPatchHelper.getContentWithPatch(linesNoPatch, patchLines, linesNoPatch.size()),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write file %s", file), e);
        }
    }

    private static List<String> getGitignorePatch(Path projectDir, List<Path> pathsToBeCommitted) {
        Stream<String> newIdeaFiles = pathsToBeCommitted.stream()
                .map(projectDir::relativize)
                .map(Path::toString)
                .map(path -> String.format("!%s", path));
        return Stream.concat(
                        Stream.concat(Stream.of(GradleJdkPatchHelper.PATCH_HEADER, ".idea/*"), newIdeaFiles),
                        Stream.of("jdk-*", GradleJdkPatchHelper.PATCH_FOOTER))
                .collect(Collectors.toList());
    }

    private static String parseToolchains(String toolchains) {
        return Stream.of(toolchains.split(","))
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.joining(","));
    }

    public static void updateGradleProperties(Path gradlePropertiesFile, Map<String, String> gradleJdkProperties) {
        try {
            // use the patching mechanism, such that we can include extra comments.
            if (!Files.exists(gradlePropertiesFile)) {
                Files.createFile(gradlePropertiesFile);
            }
            List<String> initialLines = Files.readAllLines(gradlePropertiesFile);
            List<String> linesNoPatch = GradleJdkPatchHelper.getLinesWithoutPatch(initialLines);
            List<String> linesWithoutNewProperties = linesNoPatch.stream()
                    .filter(line -> {
                        String[] keyValue = line.split("=");
                        String name = keyValue[0].trim();
                        String value = keyValue[1].trim();
                        return !gradleJdkProperties.containsKey(name)
                                || (gradleJdkProperties.containsKey(name)
                                        && gradleJdkProperties.get(name).equals(value));
                    })
                    .collect(Collectors.toList());
            if (linesWithoutNewProperties.size() != linesNoPatch.size()) {
                System.out.println(
                        "Some gradle properties from gradle.properties file were updated by the Gradle JDK setup.");
            }
            String gradleJdkPatch = getGradlePropertiesPatch(gradleJdkProperties);
            writeContentWithPatch(gradlePropertiesFile, linesWithoutNewProperties, List.of(gradleJdkPatch));
        } catch (IOException e) {
            throw new RuntimeException("Unable to update the gradle.properties file", e);
        }
    }

    public static String getGradlePropertiesPatch(Map<String, String> gradleJdkProperties) throws IOException {
        String gradleJdkLines = gradleJdkProperties.entrySet().stream()
                .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        try (InputStream inputStream =
                GradleJdkPropertiesSetup.class.getClassLoader().getResourceAsStream("gradle_properties.template")) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: gradle_properties.template");
            }
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                inputStream.transferTo(outputStream);
                return outputStream.toString(StandardCharsets.UTF_8).replace("_GRADLE_JDK_PROPERTIES_", gradleJdkLines);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read gradle_properties.template");
        }
    }

    private GradleJdkPropertiesSetup() {}
}
