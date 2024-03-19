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

package com.palantir.gradle.certs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;

public final class GradleJdkInstallationSetup {

    public static void main(String[] args) {
        StdLogger logger = new StdLogger();
        Path destinationJdkInstallationDirectory = Path.of(args[0]);
        atomicCopyJdkInstallationDirectory(logger, destinationJdkInstallationDirectory);
        CaResources.maybeImportPalantirRootCaInJdk(logger, destinationJdkInstallationDirectory);
    }

    private static void atomicCopyJdkInstallationDirectory(ILogger logger, Path destinationJdkInstallationDirectory) {
        Path currentJavaHome = Path.of(System.getProperty("java.home"));
        try (PathLock ignored = new PathLock(destinationJdkInstallationDirectory)) {
            // double-check, now that we hold the lock
            if (Files.exists(destinationJdkInstallationDirectory)) {
                logger.log(String.format("Distribution URL %s already exists", destinationJdkInstallationDirectory));
                return;
            }
            logger.log(
                    String.format("Copying JDK from %s into %s", currentJavaHome, destinationJdkInstallationDirectory));
            FileUtils.copyDirectory(currentJavaHome.toFile(), destinationJdkInstallationDirectory.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Unable to acquire locks, won't move the JDK installation directory", e);
        }
    }

    private GradleJdkInstallationSetup() {}
}
