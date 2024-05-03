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

import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectories;

@AutoParallelizable
public class UpdateGradleJdks {

    interface Params {

        @Input
        ListProperty<JavaVersion> getJdks();

        @OutputDirectories
        MapProperty<JavaVersion, Directory> getJdkToGradleJdkDirectory();
    }

    public abstract static class UpdateGradleJdksTask extends UpdateGradleJdksTaskImpl {}

    static void action(Params params) {
        Arch arch = CurrentArch.get();
        Os os = CurrentOs.get();
        params.getJdks().get().forEach(jdk -> {
            try {
                Directory outputDir = params.getJdkToGradleJdkDirectory()
                        .get()
                        .get(jdk.getMajorVersion())
                        .dir(os.uiName())
                        .dir(arch.uiName());
                Files.createDirectories(outputDir.getAsFile().toPath());
                File downloadUrlFile = outputDir.file("download-url").getAsFile();
                maybeCreateFile(downloadUrlFile);
                File localPath = outputDir.file("local-path").getAsFile();
                maybeCreateFile(localPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void maybeCreateFile(File file) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
    }
}
