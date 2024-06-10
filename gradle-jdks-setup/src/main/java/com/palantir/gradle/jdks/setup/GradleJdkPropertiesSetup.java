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

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GradleJdkPropertiesSetup {

    public static void main(String[] args) {
        Path projectDir = Path.of(args[0]);
        String gradleJdkSymlink = parseGradleJdk(args[1]);
        String allJdkSymlinks = parseToolchains(args[2]);
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
        // [Intelij specific] Set the gradle java home using GRADLE_LOCAL_JAVA_HOME config.properties read by Intelij
        try {
            Files.createDirectories(projectDir.resolve(".gradle"));
            Files.write(
                    projectDir.resolve(".gradle/config.properties"),
                    String.format("java.home=%s", gradleJdkSymlink).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to set the java.home value in .gradle/config.properties.", e);
        }

        // [Intelij] Update the .idea files with the startup script and the GRADLE_LOCAL_JAVA_HOME gradleJvm setup
        Path targetProjectIdea = projectDir.resolve(".idea");
        writeIdeaFiles(targetProjectIdea);

        // [Intelij] Update .gitignore to not ignore the newly added .idea files & ignore the jdk-* symlinks
        updateIdeaGitignore(projectDir.resolve(".gitignore"), FileUtils.collectRelativePaths(targetProjectIdea));
    }

    private static void writeIdeaFiles(Path targetProjectIdea) {
        URL ideaConfigurations = GradleJdkPropertiesSetup.class.getClassLoader().getResource("ideaConfigurations");
        try {
            copyJarResourcesRecursively(targetProjectIdea, (JarURLConnection) ideaConfigurations.openConnection());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyJarResourcesRecursively(Path destination, JarURLConnection jarConnection)
            throws IOException {
        JarFile jarFile = jarConnection.getJarFile();
        for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
            JarEntry entry = it.next();
            if (entry.getName().startsWith(jarConnection.getEntryName())
                    && !entry.getName().equals(jarConnection.getEntryName())) {
                String entryName = entry.getName().replace(jarConnection.getEntryName() + "/", "");
                if (!entry.isDirectory()) {
                    try (InputStream entryInputStream = jarFile.getInputStream(entry)) {
                        if (entryName.endsWith("gradle.xml") && Files.exists(destination.resolve(entryName))) {
                            XmlPatcher.updateGradleJvmValue(destination
                                    .resolve(entryName)
                                    .toAbsolutePath()
                                    .toString());
                        } else {
                            Files.copy(
                                    entryInputStream,
                                    destination.resolve(entryName),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    FileUtils.createDirectories(destination.resolve(entryName));
                }
            }
        }
    }

    private static void updateIdeaGitignore(Path gitignoreFile, List<Path> pathsToBeCommitted) {
        // try to add the lines after ".idea/" in .gitignore if it exists
        try {

            if (!Files.exists(gitignoreFile)) {
                Files.createFile(gitignoreFile);
            }
            List<String> gitignoreLines = Files.readAllLines(gitignoreFile);
            int ideaIndex = gitignoreLines.indexOf(".idea/");
            if (ideaIndex != -1) {
                gitignoreLines.remove(ideaIndex);
                gitignoreLines.add(ideaIndex, ".idea/*");
            }
            List<String> gitignorePatch = getGitignorePatch(pathsToBeCommitted);
            writeContentWithPatch(gitignoreFile, gitignoreLines, gitignorePatch);

        } catch (IOException e) {
            throw new RuntimeException("Unable to update the .gitignore file.", e);
        }
    }

    public static void writeContentWithPatch(Path file, List<String> initialLines, List<String> patchLines) {
        List<String> linesNoPatch = GradleJdkPatchHelper.getLinesWithoutPatch(initialLines);
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

    private static List<String> getGitignorePatch(List<Path> pathsToBeCommitted) {
        Stream<String> newIdeaFiles =
                pathsToBeCommitted.stream().map(Path::toString).map(s -> "!.idea/" + s);
        return Stream.concat(
                        Stream.concat(Stream.of(GradleJdkPatchHelper.PATCH_HEADER, ".idea/*"), newIdeaFiles),
                        Stream.of("jdk-*", GradleJdkPatchHelper.PATCH_FOOTER))
                .collect(Collectors.toList());
    }

    private static String parseGradleJdk(String gradleJdk) {
        return Path.of(gradleJdk).toString();
    }

    private static String parseToolchains(String toolchains) {
        return Stream.of(toolchains.split(","))
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.joining(","));
    }

    public static void updateGradleProperties(Path gradlePropertiesFile, Map<String, String> properties) {
        try {
            if (!Files.exists(gradlePropertiesFile)) {
                Files.createFile(gradlePropertiesFile);
            }
            List<String> initialLines = Files.readAllLines(gradlePropertiesFile);
            Set<String> presentProperties = initialLines.stream()
                    .map(line -> {
                        String[] keyValue = line.split("=");
                        return keyValue[0].trim();
                    })
                    .collect(Collectors.toSet());
            Stream<String> newProperties = properties.entrySet().stream()
                    .filter(entry -> !presentProperties.contains(entry.getKey()))
                    .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()));
            Stream<String> updatedProperties = initialLines.stream().map(line -> {
                String[] keyValue = line.split("=");
                String key = keyValue[0].trim();
                if (properties.containsKey(key)) {
                    return String.format("%s=%s", key, properties.get(keyValue[0]));
                }
                return line;
            });

            Files.write(
                    gradlePropertiesFile,
                    Stream.concat(updatedProperties, newProperties).collect(Collectors.toList()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to update the gradle.properties file", e);
        }
    }

    private GradleJdkPropertiesSetup() {}
}
