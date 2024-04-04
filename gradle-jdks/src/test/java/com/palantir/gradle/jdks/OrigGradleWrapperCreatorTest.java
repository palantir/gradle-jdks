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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class OrigGradleWrapperCreatorTest {

    public Path buildDir = Path.of("build/testing");

    @Test
    public void can_generate_new_gradle_wrapper_class() {
        JarResources.extractJar(Path.of("../gradle/wrapper/gradle-wrapper.jar").toFile(), buildDir);
        OrigGradleWrapperCreator.create(buildDir);
        buildDir.resolve("org/gradle/wrapper/OrigGradleWrapper.class").toFile().exists();
    }
}
