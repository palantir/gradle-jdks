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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GradleJdkPropertiesSetupTest {

    @TempDir
    Path tempDir;

    @Test
    public void can_update_gradle_properties() throws IOException {
        Path gradleProperties = tempDir.resolve("gradle.properties");
        Path originalFileWithPatch = Path.of("src/test/resources/initial_gradle.properties");
        Files.copy(originalFileWithPatch, gradleProperties);
        GradleJdkPropertiesSetup.updateGradleProperties(
                gradleProperties, Map.of("prop1", "updated", "prop3", "value3"));
        String expectedOutput = Files.readString(Path.of("src/test/resources/expected_updated_gradle.properties"));
        assertThat(Files.readString(gradleProperties)).isEqualTo(expectedOutput);

        // running the second time yield the same result
        GradleJdkPropertiesSetup.updateGradleProperties(
                gradleProperties, Map.of("prop1", "updated", "prop3", "value3"));
        assertThat(Files.readString(gradleProperties)).isEqualTo(expectedOutput);
    }

    @Test
    public void can_add_gradle_properties() throws IOException {
        Path gradleProperties = tempDir.resolve("gradle.properties");
        Path originalFileWithPatch = Path.of("src/test/resources/initial_gradle.properties");
        Files.copy(originalFileWithPatch, gradleProperties);
        GradleJdkPropertiesSetup.updateGradleProperties(gradleProperties, Map.of("prop3", "value3", "prop4", "value4"));
        String expectedOutput = Files.readString(Path.of("src/test/resources/expected_added_gradle.properties"));
        assertThat(Files.readString(gradleProperties)).isEqualTo(expectedOutput);

        // running the second time yield the same result
        GradleJdkPropertiesSetup.updateGradleProperties(gradleProperties, Map.of("prop3", "value3", "prop4", "value4"));
        assertThat(Files.readString(gradleProperties)).isEqualTo(expectedOutput);
    }

    @Test
    public void can_update_gitignore_file() throws IOException {
        Path gitignore = tempDir.resolve(".gitignore");
        Path originalGitignore = Path.of("src/test/resources/initial.gitignore");
        Files.copy(originalGitignore, gitignore);
        Path ideaDir = Files.createDirectories(tempDir.resolve(".idea"));
        Path gradleXml = Files.createFile(ideaDir.resolve("gradle.xml"));
        Path anotherFile = Files.createFile(tempDir.resolve("anotherFile"));
        GradleJdkPropertiesSetup.updateIdeaGitignore(tempDir, List.of(gradleXml, anotherFile));
        String expectedOutput = Files.readString(Path.of("src/test/resources/expected.gitignore"));
        assertThat(Files.readString(gitignore)).isEqualTo(expectedOutput);

        // running the second time yield the same result
        GradleJdkPropertiesSetup.updateIdeaGitignore(tempDir, List.of(gradleXml, anotherFile));
        assertThat(Files.readString(gitignore)).isEqualTo(expectedOutput);
    }
}
