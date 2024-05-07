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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

@AutoParallelizable
public class UpdateGradleJdks {

    private static final Logger log = Logging.getLogger(UpdateGradleJdks.class);

    interface JdkDistribution {

        @Input
        Property<String> getDownloadUrl();

        @Input
        Property<String> getLocalPath();
    }

    interface Params {

        @Nested
        MapProperty<JavaLanguageVersion, JdkDistribution> getJavaVersionToJdkDistro();

        @Input
        Property<JavaLanguageVersion> getDaemonJavaVersion();

        @OutputFile
        RegularFileProperty getDaemonJdkFile();

        @OutputDirectories
        MapProperty<JavaLanguageVersion, Directory> getGradleJdkDirectories();
    }

    public abstract static class UpdateGradleJdksTask extends UpdateGradleJdksTaskImpl {}

    static void action(Params params) {
        Arch arch = CurrentArch.get();
        Os os = CurrentOs.get();
        params.getJavaVersionToJdkDistro().get().forEach((javaVersion, jdkDistribution) -> {
            try {
                Directory outputDir = params.getGradleJdkDirectories()
                        .get()
                        .get(javaVersion)
                        .dir(os.uiName())
                        .dir(arch.uiName());
                Files.createDirectories(outputDir.getAsFile().toPath());
                File downloadUrlFile = outputDir.file("download-url").getAsFile();
                writeToFile(downloadUrlFile, jdkDistribution.getDownloadUrl().get());
                File localPath = outputDir.file("local-path").getAsFile();
                writeToFile(localPath, jdkDistribution.getLocalPath().get());
                writeToFile(
                        params.getDaemonJdkFile().get().getAsFile(),
                        params.getDaemonJavaVersion().get().toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void writeToFile(File file, String content) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
        String contentWithLineEnding = content + "\n";
        Files.write(file.toPath(), contentWithLineEnding.getBytes(StandardCharsets.UTF_8));
    }
}
