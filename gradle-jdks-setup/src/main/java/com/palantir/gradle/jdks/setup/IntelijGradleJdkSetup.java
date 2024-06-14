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

import com.palantir.gradle.jdks.CommandRunner;
import com.palantir.gradle.jdks.CurrentOs;
import com.palantir.gradle.jdks.GradleJdkPatchHelper;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("BanSystemOut")
public final class IntelijGradleJdkSetup {

    private IntelijGradleJdkSetup() {}

    public static void main(String[] args) throws Exception {
        Path projectDir = Path.of(args[0]);
        switch (CurrentOs.get()) {
            case MACOS:
            case LINUX_MUSL:
            case LINUX_GLIBC:
                unixRunGradleJdksSetup(projectDir);
                break;
            case WINDOWS:
                maybeDisableGradleJdkSetup(projectDir);
                break;
        }
    }

    @SuppressWarnings("BanSystemOut")
    private static void unixRunGradleJdksSetup(Path projectDir) {
        System.out.println("Generating the Gradle JDK configurations...");
        // 1. generate all the `gradle/` configurations first
        CommandRunner.runWithInheritIO(List.of("./gradlew", "generateGradleJdkConfigs"), projectDir.toFile());
        // 2. run the ./gradle/gradle-jdks-setup.sh script to install the JDKs. We cannot run directly `./gradlew`
        // because the current JVM might not have the certificates we need (eg. Palantir certs) when running Gradle
        // tasks.
        System.out.println("Installing and setting up the JDKs...");
        CommandRunner.runWithInheritIO(List.of("./gradle/gradle-jdks-setup.sh"), projectDir.toFile());

        // [Intelij] Patch gradle.xml file to set gradleJvm to #GRADLE_LOCAL_JAVA_HOME
        Path gradleXml = projectDir.resolve(".idea/gradle.xml");
        if (gradleXml.toFile().exists()) {
            XmlPatcher.updateGradleJvmValue(gradleXml);
        } else {
            throw new RuntimeException(
                    "Could not update the gradle configuration programmatically. Please configure `Gradle JVM` to "
                            + " GRADLE_LOCAL_JAVA_HOME in ( Settings | Build, Execution, Deployment | Build Tools | "
                            + "Gradle | Gradle JVM).");
        }
    }

    private static void maybeDisableGradleJdkSetup(Path projectDir) {
        // On Windows, we want to revert the gradle.properties Gradle JDK setup patch for now
        GradleJdkPatchHelper.maybeRemovePatch(projectDir.resolve("gradle.properties"));
    }
}
