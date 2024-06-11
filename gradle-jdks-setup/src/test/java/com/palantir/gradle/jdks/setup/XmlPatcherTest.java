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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xmlunit.assertj.XmlAssert;

public class XmlPatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void can_patch_gradle_xml_file() throws IOException {
        Path gradleXmlFile = tempDir.resolve("gradle.xml");
        Files.copy(Path.of("src/test/resources/idea_generated_gradle.xml"), gradleXmlFile);
        XmlPatcher.updateGradleJvmValue(gradleXmlFile.toAbsolutePath());
        XmlAssert.assertThat(Files.readString(gradleXmlFile))
                .and(Path.of("src/test/resources/expected_idea_patched_gradle.xml"))
                .ignoreWhitespace()
                .areIdentical();
    }
}
