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
import com.palantir.gradle.jdks.setup.common.CommandRunner;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class GradleJdkInstallationSetupTest {

    private static final BigInteger AMAZON_ROOT_CA_1_SERIAL =
            new BigInteger("143266978916655856878034712317230054538369994");
    private static final String AMAZON_CERT_ALIAS = "AmazonRootCA1Test";

    @TempDir
    Path tempDir;

    @Test
    public void can_copy_jdk_installation_with_no_certs() throws IOException {
        String distribution =
                Path.of(System.getProperty("java.home")).getFileName().toString();
        Path certsDir = Files.createDirectories(tempDir.resolve("certs"));
        GradleJdkInstallationSetup.main(new String[] {
            Command.JDK_SETUP.toString(),
            tempDir.resolve(distribution).toAbsolutePath().toString(),
            certsDir.toAbsolutePath().toString()
        });
        Path destDistribution = tempDir.resolve(distribution);
        Path destJavaHome = destDistribution.resolve("bin/java");
        assertThat(destDistribution).exists();
        assertThat(destJavaHome).exists();
    }

    @Test
    public void can_copy_jdk_installation_with_palantir_certs() throws IOException {
        String distribution =
                Path.of(System.getProperty("java.home")).getFileName().toString();
        Path certsDir = Files.createDirectories(tempDir.resolve("certs"));
        Path amazonRootCa = Files.createFile(certsDir.resolve(String.format("%s.serial-number", AMAZON_CERT_ALIAS)));
        Files.write(amazonRootCa, AMAZON_ROOT_CA_1_SERIAL.toString().getBytes(StandardCharsets.UTF_8));
        GradleJdkInstallationSetup.main(new String[] {
            Command.JDK_SETUP.toString(),
            tempDir.resolve(distribution).toAbsolutePath().toString(),
            certsDir.toAbsolutePath().toString()
        });
        Path destDistribution = tempDir.resolve(distribution);
        Path destJavaHome = destDistribution.resolve("bin/java");
        assertThat(destDistribution).exists();
        assertThat(destJavaHome).exists();
        checkCaIsImported(destDistribution);
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

    private static void checkCaIsImported(Path jdkPath) {
        CommandRunner.run(List.of(
                jdkPath.resolve("bin/keytool").toString(),
                "-list",
                "-storepass",
                "changeit",
                "-alias",
                AMAZON_CERT_ALIAS,
                "-keystore",
                jdkPath.resolve("lib/security/cacerts").toString()));
    }
}
