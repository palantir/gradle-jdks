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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FileUtils {

    public static List<Path> collectRelativePaths(Path source) {
        try {
            List<Path> paths = new ArrayList<>();
            Files.walkFileTree(source, new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path _dir, BasicFileAttributes _attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) {
                    paths.add(source.relativize(file));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path _file, IOException _exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path _dir, IOException _exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
            return paths;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not collect the files in directory %s", source), e);
        }
    }

    public static void copyDirectory(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes _attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path _file, IOException exc) throws IOException {
                throw new RuntimeException("Failed to copy file", exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path _dir, IOException exc) throws IOException {
                if (Optional.ofNullable(exc).isPresent()) {
                    throw new RuntimeException("Failed to copy directory", exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void createDirectories(Path directoryPath) {
        try {
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not create directory %s", directoryPath), e);
        }
    }

    private FileUtils() {}
}
