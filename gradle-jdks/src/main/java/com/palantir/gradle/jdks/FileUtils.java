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

import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

public final class FileUtils {

    public static void checkFilesAreTheSame(File originalPath, File outputPath) {
        checkFilesAreTheSame(originalPath, outputPath, Optional.empty());
    }

    public static void checkFilesAreTheSame(
            File originalPath, File outputPath, Optional<Supplier<ExceptionWithSuggestion>> exceptionIfFailed) {
        try {
            byte[] originalBytes = Files.readAllBytes(originalPath.toPath());
            byte[] outputBytes = Files.readAllBytes(outputPath.toPath());
            if (!Arrays.equals(originalBytes, outputBytes)) {
                throw exceptionIfFailed
                        .orElseThrow(() -> new RuntimeException("Files are not the same"))
                        .get();
            }
        } catch (IOException e) {
            throw exceptionIfFailed.map(Supplier::get).orElseThrow(() -> new RuntimeException(e));
        }
    }

    private FileUtils() {}
}
