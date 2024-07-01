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

package com.palantir.gradle.jdks.setup.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleJdkPatchHelperTest {

    @TempDir
    Path tmpDir;

    @Test
    void correctly_removes_patch() throws IOException {
        Path originalFileWithPatch = Path.of("src/test/resources/file_with_patch.txt");
        Path originalFileNoPatch = Path.of("src/test/resources/file_no_patch.txt");
        Path processedFile = Files.copy(originalFileWithPatch, tmpDir.resolve("file_with_patch.txt"));
        GradleJdkPatchHelper.maybeRemovePatch(processedFile);
        assertEqualFiles(processedFile, originalFileNoPatch);
        GradleJdkPatchHelper.maybeRemovePatch(processedFile);
        assertEqualFiles(processedFile, originalFileNoPatch);
    }

    private void assertEqualFiles(Path actualPath, Path expectedPath) throws IOException {
        List<String> actualBytes = Files.readAllLines(actualPath);
        List<String> expectedBytes = Files.readAllLines(expectedPath);
        assertThat(actualBytes).isEqualTo(expectedBytes);
    }
}
