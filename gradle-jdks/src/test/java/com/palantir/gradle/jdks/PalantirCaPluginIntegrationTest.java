/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
public class PalantirCaPluginIntegrationTest {

    @Test
    void canAddCaCertsToJdk(GradleInvoker gradle, RootProject project) {
        // Configure build.gradle
        project.buildGradle()
                .append("// Can't do strict as open source CI does not have the Palantir CA\n"
                        + "plugins {\n"
                        + "    id 'com.palantir.jdks.palantir-ca'\n"
                        + "    id 'java-library'\n"
                        + "}\n"
                        + "\n"
                        + "jdks {\n"
                        + "    jdk(11) {\n"
                        + "        distribution = 'azul-zulu'\n"
                        + "        jdkVersion = '11.54.25-11.0.14.1'\n"
                        + "    }\n"
                        + "\n"
                        + "    jdkStorageLocation = layout.buildDirectory.dir('jdks')\n"
                        + "}\n"
                        + "\n"
                        + "javaVersions {\n"
                        + "    libraryTarget = 11\n"
                        + "}\n"
                        + "\n"
                        + "task printCaTruststoreAliases(type: JavaExec) {\n"
                        + "    classpath = sourceSets.main.runtimeClasspath\n"
                        + "    mainClass = 'foo.OutputCaCerts'\n"
                        + "    logging.captureStandardOutput LogLevel.LIFECYCLE\n"
                        + "    logging.captureStandardError LogLevel.LIFECYCLE\n"
                        + "}");

        // Create Java source file
        project.mainSourceSet()
                .java()
                .writeClass("package foo;\n"
                        + "\n"
                        + "import java.io.File;\n"
                        + "import java.security.KeyStore;\n"
                        + "import java.security.cert.X509Certificate;\n"
                        + "\n"
                        + "public final class OutputCaCerts {\n"
                        + "    public static void main(String... args) throws Exception {\n"
                        + "        KeyStore keyStore = KeyStore.getInstance(\n"
                        + "                new File(System.getProperty(\"java.home\"), \"lib/security/cacerts\"),\n"
                        + "                \"changeit\".toCharArray());\n"
                        + "        X509Certificate palantirCert = ((X509Certificate) keyStore.getCertificate(\"Palantir3rdGenRootCa\"));\n"
                        + "\n"
                        + "        if (palantirCert != null) {\n"
                        + "            System.out.println(palantirCert.getSerialNumber());\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");

        // Run the task
        InvocationResult result = gradle.withArgs("printCaTruststoreAliases").buildsSuccessfully();
        String stdout = result.output();

        String palantir3rdGenCaSerial = "18126334688741185161";

        // Check output - Skip assertion in CI environment
        if (System.getenv("CI") == null) {
            assertTrue(stdout.contains(palantir3rdGenCaSerial), "Output should contain Palantir CA serial number");
        }
    }
}