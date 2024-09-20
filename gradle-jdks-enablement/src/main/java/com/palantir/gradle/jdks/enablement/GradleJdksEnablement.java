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

package com.palantir.gradle.jdks.enablement;

import com.palantir.gradle.jdks.setup.common.CurrentOs;
import com.palantir.gradle.jdks.setup.common.Os;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class GradleJdksEnablement {

    public static final String MINIMUM_SUPPORTED_GRADLE_VERSION = "7.6";

    public static boolean isGradleJdkSetupEnabled(Path projectDir) {
        return !CurrentOs.get().equals(Os.WINDOWS) && isGradleJdkPropertyEnabled(projectDir);
    }

    private static boolean isGradleJdkPropertyEnabled(Path projectDir) {
        File gradlePropsFile = projectDir.resolve("gradle.properties").toFile();
        if (!gradlePropsFile.exists()) {
            return false;
        }
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(gradlePropsFile));
            return Optional.ofNullable(properties.getProperty("palantir.jdk.setup.enabled"))
                    .map(Boolean::parseBoolean)
                    .orElse(false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read gradle.properties file", e);
        }
    }

    private GradleJdksEnablement() {}
}
