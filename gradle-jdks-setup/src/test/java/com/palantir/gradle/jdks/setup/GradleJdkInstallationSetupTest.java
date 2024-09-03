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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.jdks.setup.GradleJdkInstallationSetup.Command;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class GradleJdkInstallationSetupTest {

    @TempDir
    Path tempDir;

    @Test
    public void can_copy_jdk_installation_with_no_certs() {
        String distribution =
                Path.of(System.getProperty("java.home")).getFileName().toString();
        GradleJdkInstallationSetup.main(new String[] {
            Command.JDK_SETUP.toString(),
            tempDir.resolve(distribution).toAbsolutePath().toString(),
        });
        Path destDistribution = tempDir.resolve(distribution);
        Path destJavaHome = destDistribution.resolve("bin/java");
        assertThat(destDistribution).exists();
        assertThat(destJavaHome).exists();
    }

    @Test
    public void can_write_gradle_config_file() throws IOException {
        Path gradleConfig = tempDir.resolve(".gradle/config.properties");
        GradleJdkInstallationSetup.main(
                new String[] {Command.DAEMON_SETUP.toString(), tempDir.toString(), "my_directory"});
        assertThat(Files.readString(gradleConfig)).contains("java.home=my_directory");
    }

    @Test
    public void can_update_gradle_config_file() throws IOException {
        Files.createDirectories(tempDir.resolve(".gradle"));
        Path gradleConfig = tempDir.resolve(".gradle/config.properties");
        Files.write(
                gradleConfig,
                "# comment\njava.home=initial_value\nkey1=value1".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE);
        GradleJdkInstallationSetup.main(
                new String[] {Command.DAEMON_SETUP.toString(), tempDir.toString(), "my_directory"});
        assertThat(Files.readString(gradleConfig))
                .contains("java.home=my_directory")
                .contains("key1=value1")
                .doesNotContain("java.home=initial_value");
    }
}
