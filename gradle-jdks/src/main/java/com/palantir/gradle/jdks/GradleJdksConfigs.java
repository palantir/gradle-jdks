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

import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * The two tasks {@link CheckGradleJdksConfigsTask} and {@link GenerateGradleJdksConfigsTask} need to either check the
 * validity of the already generated gradle files or generate the gradle files.
 */
public abstract class GradleJdksConfigs extends DefaultTask {

    public static final String GRADLE_JDKS_SETUP_JAR = "gradle-jdks-setup.jar";
    public static final String GRADLE_JDKS_SETUP_SCRIPT = "gradle-jdks-setup.sh";
    public static final String GRADLE_JDKS_FUNCTIONS_SCRIPT = "gradle-jdks-functions.sh";

    @Nested
    public abstract MapProperty<JavaLanguageVersion, List<JdkDistributionConfig>> getJavaVersionToJdkDistros();

    @Input
    public abstract Property<JavaLanguageVersion> getDaemonJavaVersion();

    @Input
    public abstract MapProperty<String, String> getCaCerts();

    abstract Directory gradleDirectory();

    protected abstract void applyGradleJdkFileAction(
            Path downloadUrlPath, Path localUrlPath, JdkDistributionConfig jdkDistributionConfig);

    protected abstract void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion);

    protected abstract void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName);

    protected abstract void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName);

    protected abstract void applyCertAction(File certFile, String alias, String content);

    @TaskAction
    public final void action() {
        AtomicBoolean jdksDirectoryConfigured = new AtomicBoolean(false);
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
                jdksDirectoryConfigured.set(true);
            });
        });
        if (!jdksDirectoryConfigured.get()) {
            throw new RuntimeException(
                    "No JDKs were configured for the gradle setup. Please run `./gradlew setupJdks` to generate the"
                            + " JDKs and ensure that you have configured JDKs properly for gradle-jdks as per"
                            + " the readme: https://github.com/palantir/gradle-jdks#usage");
        }

        String gradleJdkDaemonVersion = getDaemonJavaVersion().get().toString();
        String os = CurrentOs.get().toString();
        String arch = CurrentArch.get().toString();
        Path expectedJdkDir = gradleDirectory()
                .dir("jdks")
                .getAsFile()
                .toPath()
                .resolve(gradleJdkDaemonVersion)
                .resolve(os)
                .resolve(arch);
        if (!Files.exists(expectedJdkDir)) {
            throw new RuntimeException(String.format(
                    "Gradle daemon JDK version is `%s` but no JDK configured for that version. Please ensure that you"
                            + " have configured JDKs properly for gradle-jdks as per the readme:"
                            + " https://github.com/palantir/gradle-jdks#usage and the gradle daemon version specified "
                            + "in the jdks#daemonTarget extension is correctly set",
                    gradleJdkDaemonVersion));
        }
        applyGradleJdkDaemonVersionAction(gradleDirectory().getAsFile().toPath().resolve("gradle-daemon-jdk-version"));

        applyGradleJdkJarAction(gradleDirectory().file(GRADLE_JDKS_SETUP_JAR).getAsFile(), GRADLE_JDKS_SETUP_JAR);
        applyGradleJdkScriptAction(
                gradleDirectory().file(GRADLE_JDKS_FUNCTIONS_SCRIPT).getAsFile(), GRADLE_JDKS_FUNCTIONS_SCRIPT);
        applyGradleJdkScriptAction(
                gradleDirectory().file(GRADLE_JDKS_SETUP_SCRIPT).getAsFile(), GRADLE_JDKS_SETUP_SCRIPT);

        File certsDir = gradleDirectory().file("certs").getAsFile();
        getCaCerts().get().forEach((alias, content) -> {
            File certFile = new File(certsDir, String.format("%s.serial-number", alias));
            applyCertAction(certFile, alias, content);
        });
    }
}
