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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OriginalGradleWrapperMainCreatorTest {

    @TempDir
    public Path buildDir;

    @Test
    public void can_generate_new_gradle_wrapper_class() {
        JarResources.extractJar(Path.of("../gradle/wrapper/gradle-wrapper.jar").toFile(), buildDir);
        OriginalGradleWrapperMainCreator.create(buildDir);
        Path classPath = buildDir.resolve("org/gradle/wrapper/OriginalGradleWrapperMain.class");
        String decompiledOutput = CommandRunner.run(List.of("javap", "-c", "-private", classPath.toString()));
        assertThat(decompiledOutput).contains("Compiled from \"GradleWrapperMain.java\"");
        int originalGradleWrapperMainMatches = StringUtils.countMatches(decompiledOutput, "OriginalGradleWrapperMain");
        assertThat(StringUtils.countMatches(decompiledOutput, "GradleWrapperMain"))
                .isEqualTo(1 + originalGradleWrapperMainMatches);
        assertThat(decompiledOutput).contains("OriginalGradleWrapperMain");
    }
}
