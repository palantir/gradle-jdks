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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import org.gradle.api.file.Directory;

public final class FileUtils {

    public static void checkFilesAreTheSame(
            File originalPath, File outputPath, ExceptionWithSuggestion exceptionIfFailed) {
        try {
            byte[] originalBytes = Files.readAllBytes(originalPath.toPath());
            byte[] outputBytes = Files.readAllBytes(outputPath.toPath());
            if (!Arrays.equals(originalBytes, outputBytes)) {
                throw exceptionIfFailed;
            }
        } catch (IOException e) {
            throw exceptionIfFailed;
        }
    }

    @VisibleForTesting
    static void checkFilesAreTheSame(File originalPath, File outputPath) {
        try {
            byte[] originalBytes = Files.readAllBytes(originalPath.toPath());
            byte[] outputBytes = Files.readAllBytes(outputPath.toPath());
            if (!Arrays.equals(originalBytes, outputBytes)) {
                throw new RuntimeException("Files are not the same");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void checkDirectoriesAreTheSame(
            Directory dir1, Directory dir2, ExceptionWithSuggestion exceptionIfFailed) {
        try {
            Files.walkFileTree(dir1.getAsFile().toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileVisitResult result = super.visitFile(file, attrs);
                    Path relativize = dir1.getAsFile().toPath().relativize(file);
                    Path fileInOther = dir2.getAsFile().toPath().resolve(relativize);
                    byte[] otherBytes = Files.readAllBytes(fileInOther);
                    byte[] theseBytes = Files.readAllBytes(file);
                    if (!Arrays.equals(otherBytes, theseBytes)) {
                        throw exceptionIfFailed;
                    }
                    return result;
                }
            });
        } catch (IOException e) {
            throw exceptionIfFailed;
        }
    }

    private FileUtils() {}
}
