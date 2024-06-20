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
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("BanSystemOut")
public final class IntelijGradleJdkSetup {

    public IntelijGradleJdkSetup() {}

    @SuppressWarnings("BanSystemOut")
    public static void main(String[] args) throws Exception {
        Path projectDir = Path.of(args[0]);
        System.out.println("Generating the Gradle JDK configurations...");
        // 1. generate all the `gradle/` configurations first
        CommandRunner.runWithInheritIO(List.of("./gradlew", "generateGradleJdkConfigs"), projectDir.toFile());
        // 2. run the ./gradle/gradle-jdks-setup.sh script to install the JDKs. We cannot run directly `./gradlew`
        // because the current JVM might not have the certificates we need (eg. Palantir certs) when running Gradle
        // tasks.
        System.out.println("Installing and setting up the JDKs...");
        CommandRunner.runWithInheritIO(List.of("./gradle/gradle-jdks-setup.sh"), projectDir.toFile());
    }
}
