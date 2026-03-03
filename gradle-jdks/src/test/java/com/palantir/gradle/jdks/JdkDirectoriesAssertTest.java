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

import static com.palantir.gradle.jdks.JdkDirectoriesAssert.assertThatJdkDirectories;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkDirectoriesAssertTest {

    @TempDir
    Path tempDir;

    @Test
    void passes_when_directories_match_expected_versions() throws IOException {
        Files.createDirectory(tempDir.resolve("11"));
        Files.createDirectory(tempDir.resolve("17"));

        assertThatJdkDirectories(tempDir).containsExactJdks(11, 17);
    }

    @Test
    void fails_when_directories_do_not_match() throws IOException {
        Files.createDirectory(tempDir.resolve("11"));
        Files.createDirectory(tempDir.resolve("17"));

        assertThatThrownBy(() -> assertThatJdkDirectories(tempDir).containsExactJdks(11, 21))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected JDK directories to contain exactly versions")
                .hasMessageContaining("[11, 21]")
                .hasMessageContaining("JdkDirectory[version=11")
                .hasMessageContaining("JdkDirectory[version=17");
    }

    @Test
    void ignores_non_directory_files() throws IOException {
        Files.createDirectory(tempDir.resolve("17"));
        Files.createFile(tempDir.resolve("README"));

        assertThatJdkDirectories(tempDir).containsExactJdks(17);
    }

    @Test
    void works_with_as_description() throws IOException {
        Files.createDirectory(tempDir.resolve("11"));

        assertThatThrownBy(() -> assertThatJdkDirectories(tempDir)
                        .as("custom description")
                        .containsExactJdks(21))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("custom description");
    }

    @Test
    void containsJdk_passes_when_subset_present() throws IOException {
        Files.createDirectory(tempDir.resolve("11"));
        Files.createDirectory(tempDir.resolve("17"));
        Files.createDirectory(tempDir.resolve("21"));

        assertThatJdkDirectories(tempDir).containsJdk(11, 17);
    }

    @Test
    void containsJdk_fails_when_version_missing() throws IOException {
        Files.createDirectory(tempDir.resolve("11"));
        Files.createDirectory(tempDir.resolve("17"));

        assertThatThrownBy(() -> assertThatJdkDirectories(tempDir).containsJdk(11, 21))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected JDK directories to contain versions")
                .hasMessageContaining("[11, 21]");
    }
}
