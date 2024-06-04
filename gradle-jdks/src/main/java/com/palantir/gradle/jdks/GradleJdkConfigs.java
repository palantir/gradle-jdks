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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Abstracts the actions that need to be applied on the generated `gradle/` files.
 * The two tasks {@link CheckGradleJdkConfigsTask} and {@link GenerateGradleJdkConfigsTask} need to either check the
 * validity of the already generated gradle files or generate the gradle files.
 */
public abstract class GradleJdkConfigs extends DefaultTask {

    public static final String GRADLE_JDKS_SETUP_JAR = "gradle-jdks-setup.jar";
    public static final String GRADLE_JDKS_SETUP_SCRIPT = "gradle-jdks-setup.sh";

    @Nested
    public abstract MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

    @Input
    public abstract Property<JavaLanguageVersion> getDaemonJavaVersion();

    @Input
    public abstract MapProperty<String, String> getCaCerts();

    abstract Directory gradleDirectory();

    public abstract void applyGradleJdkFileAction(
            Path downloadUrlPath, Path localUrlPath, JdkDistributionConfig jdkDistributionConfig);

    public abstract void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion);

    public abstract void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName);

    public abstract void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName);

    public abstract void applyCertAction(File certFile, String alias, String content);

    @TaskAction
    public final void action() {
        getJavaVersionToJdkDistros().get().forEach((javaVersion, jdkDistros) -> {
            jdkDistros.forEach(jdkDistribution -> {
                Path outputDir = gradleDirectory()
                        .dir("jdks")
                        .getAsFile()
                        .toPath()
                        .resolve(javaVersion.toString())
                        .resolve(jdkDistribution.getOs().get().uiName())
                        .resolve(jdkDistribution.getArch().get().uiName());
                Path downloadUrlPath = outputDir.resolve("download-url");
                Path localPath = outputDir.resolve("local-path");
                applyGradleJdkFileAction(downloadUrlPath, localPath, jdkDistribution);
            });
        });

        applyGradleJdkDaemonVersionAction(gradleDirectory().getAsFile().toPath().resolve("gradle-daemon-jdk-version"));

        applyGradleJdkJarAction(gradleDirectory().file(GRADLE_JDKS_SETUP_JAR).getAsFile(), GRADLE_JDKS_SETUP_JAR);
        applyGradleJdkScriptAction(
                gradleDirectory().file(GRADLE_JDKS_SETUP_SCRIPT).getAsFile(), GRADLE_JDKS_SETUP_SCRIPT);

        File certsDir = gradleDirectory().file("certs").getAsFile();
        getCaCerts().get().forEach((alias, content) -> {
            File certFile = new File(certsDir, String.format("%s.serial-number", alias));
            applyCertAction(certFile, alias, content);
        });
    }
}
