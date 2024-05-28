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

import com.palantir.gradle.jdks.GenerateGradleJdkConfigs.GenerateGradleJdkConfigsTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.gradle.api.file.Directory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class GradleJdkConfigResources {

    public static final String JDKS_DIR = "jdks";
    public static final String CERTS = "certs";
    public static final String GRADLE_DAEMON_JDK_VERSION = "gradle-daemon-jdk-version";
    public static final String GRADLE_JDKS_SETUP_JAR = "gradle-jdks-setup.jar";
    public static final String GRADLE_JDKS_SETUP_SCRIPT = "gradle-jdks-setup.sh";
    public static final String DOWNLOAD_URL = "download-url";
    public static final String LOCAL_PATH = "local-path";

    public static Path resolveJdkOsArchPath(
            Directory gradleDirectory, JavaLanguageVersion javaVersion, JdkDistributionConfig jdkDistribution) {
        return gradleDirectory
                .dir(GradleJdkConfigResources.JDKS_DIR)
                .getAsFile()
                .toPath()
                .resolve(javaVersion.toString())
                .resolve(jdkDistribution.getOs().get().uiName())
                .resolve(jdkDistribution.getArch().get().uiName());
    }

    public static byte[] getResourceContent(String resource) {
        try (InputStream inputStream =
                GenerateGradleJdkConfigsTask.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new RuntimeException(String.format("Resource not found: %s:", resource));
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GradleJdkConfigResources() {}
}
