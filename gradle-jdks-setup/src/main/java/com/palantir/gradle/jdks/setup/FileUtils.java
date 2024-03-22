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
import java.util.Optional;

public final class FileUtils {

    public static void moveDirectory(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes _attrs) throws IOException {
                if (!destination.resolve(source.relativize(dir)).toFile().exists()) {
                    Files.createDirectories(destination.resolve(source.relativize(dir)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) throws IOException {
                Files.move(file, destination.resolve(source.relativize(file)));
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

    private FileUtils() {}
}
