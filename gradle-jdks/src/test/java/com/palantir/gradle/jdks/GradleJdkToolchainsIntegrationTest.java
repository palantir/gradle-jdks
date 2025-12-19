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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.jdks.setup.common.CurrentArch;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import com.palantir.platform.OperatingSystem;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
final class GradleJdkToolchainsIntegrationTest {

    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int JAVA_21_BYTECODE = 65;
    private static final int JAVA_23_BYTECODE = 67;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;
    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    private static final String JAVA_17_PREVIEW_CODE = """
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }

            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                switch (myUnion) {
                    case MyUnion.Foo foo -> System.out.println("Java 17 pattern matching switch: " + foo.number);
                }
                String javaHome = System.getProperty("java.home");
                System.out.println("Java home: " + javaHome);
            }
        }
        """;

    private String getMainJavaCode() {
        return """
            public class Main {
                public static void main(String[] args) {
                    String javaHome = System.getProperty("java.home");
                    System.out.println("Java home: " + javaHome);
                }
            }
            """;
    }

    private GradleFile setupJdksHardcodedVersions(RootProject rootProject) {
        return setupJdksHardcodedVersions(rootProject, GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17);
    }

    private GradleFile setupJdksHardcodedVersions(RootProject rootProject, String daemonTarget) {
        applyJdksPlugins(rootProject);

        return rootProject
                .buildGradle()
                .append(
                        """
                        jdks {
                           jdk(11) {
                              distribution = '%s'
                              jdkVersion = '%s'
                           }

                           jdk(17) {
                              distribution = '%s'
                              jdkVersion = '%s'
                           }

                           jdk(21) {
                              distribution = '%s'
                              jdkVersion = '%s'
                           }

                           daemonTarget = '%s'
                        }
                        """,
                        GradleJdkTestUtils.JDK_11.getLeft(),
                        GradleJdkTestUtils.JDK_11.getRight(),
                        GradleJdkTestUtils.JDK_17.getLeft(),
                        GradleJdkTestUtils.JDK_17.getRight(),
                        GradleJdkTestUtils.JDK_21.getLeft(),
                        GradleJdkTestUtils.JDK_21.getRight(),
                        daemonTarget);
    }

    @SuppressWarnings("GradleTestPluginsBlock")
    private void applyJdksPlugins(RootProject rootProject) {
        // Apply settings plugin with buildscript classpath injection
        rootProject.settingsGradle().prepend("""
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
                dependencies {
                    classpath files(%s)
                }
            }

            apply plugin: 'com.palantir.jdks.settings'
            """, String.join(",", getSettingsPluginClasspathInjector()));

        // Apply build plugins with buildscript classpath injection
        rootProject.buildGradle().prepend("""
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }
                dependencies {
                    classpath files(%s)
                }
            }

            apply plugin: 'java'
            apply plugin: 'com.palantir.jdks'
            apply plugin: 'com.palantir.jdks.palantir-ca'
            """, String.join(",", getBuildPluginClasspathInjector()));
    }

    @SuppressWarnings("GradleTestPluginsBlock")
    private GradleFile applyApplicationPlugin(RootProject rootProject) {
        return rootProject.buildGradle().append("""
            apply plugin: 'application'

            application {
                mainClass = 'Main'
            }
            """);
    }

    @SuppressWarnings("GradleTestPluginsBlock")
    private GradleFile applyBaselineJavaVersions(RootProject rootProject) {
        return rootProject.buildGradle().append("""
            apply plugin: 'com.palantir.baseline-java-versions'
            """);
    }

    private List<String> getBuildPluginClasspathInjector() {
        return getPluginClasspathInjector(
                Path.of("../gradle-jdks/build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"));
    }

    private List<String> getSettingsPluginClasspathInjector() {
        return getPluginClasspathInjector(
                Path.of("../gradle-jdks-settings/build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"));
    }

    private List<String> getPluginClasspathInjector(Path path) {
        try {
            File propertiesFile = path.toFile();
            if (!propertiesFile.exists()) {
                throw new RuntimeException("Plugin metadata file not found: " + path);
            }
            java.util.Properties properties = new java.util.Properties();
            try (InputStream inputStream = new FileInputStream(propertiesFile)) {
                properties.load(inputStream);
            }
            String classpath = properties.getProperty("implementation-classpath");
            if (classpath == null) {
                throw new RuntimeException("implementation-classpath property not found in " + path);
            }
            return Stream.of(classpath.split(File.pathSeparator))
                    .map(it -> "'" + it + "'")
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read plugin classpath from " + path, e);
        }
    }

    private static Pair<Integer, Integer> readBytecodeVersion(File file) throws IOException {
        try (InputStream stream = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion);
        }
    }

    private static Set<String> listJdkVersionDirectories(Path jdksDir) throws IOException {
        try (Stream<Path> paths = Files.list(jdksDir)) {
            return paths.map(it -> it.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    @Test
    void javaToolchains_correctly_set_up(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.mainSourceSet().java().writeClass(getMainJavaCode());

        rootProject.buildGradle().append("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }

            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
            """);

        gradle.withArgs("wrapper").buildsSuccessfully();

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        InvocationResult result = gradle.withArgs("setupJdks").buildsSuccessfully();

        assertThat(result)
                .output()
                .contains("Auto-detection:     Disabled")
                .contains("Auto-download:      Disabled")
                .contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_11_VERSION)
                .contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_17_VERSION)
                .contains("JDK " + GradleJdkTestUtils.SIMPLIFIED_JDK_21_VERSION);

        //        Matcher matcher = Pattern.compile("Detected by:        (.*)").matcher(result.output());
        //        while (matcher.find()) {
        //            String detectedByPattern = matcher.group(1);
        //            assertThat(detectedByPattern).contains("org.gradle.java.installations.paths");
        //        }

        InvocationResult gradleHomeOutput = gradle.withArgs("printGradleHome").buildsSuccessfully();

        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = rootProject
                .file(String.format(
                        "gradle/jdks/%s/%s/%s/local-path", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17, os, arch))
                .text()
                .trim();
        // Verify the daemon is using the configured JDK by checking the output contains the JDK directory name
        System.out.println(gradleHomeOutput.output());
        assertThat(gradleHomeOutput).output().contains("java.home:");
        assertThat(gradleHomeOutput).output().contains(daemonJdkFileName.replace("\\", "/"));

        gradle.withArgs("compileJava").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .file("classes/java/main/Main.class")
                .path()
                .toFile();
        assertThat(readBytecodeVersion(compiledClass)).isEqualTo(Pair.of(0, JAVA_17_BYTECODE));

        InvocationResult runOutput = gradle.withArgs("run").buildsSuccessfully();

        String compileJdkFileName = rootProject
                .file(String.format("gradle/jdks/17/%s/%s/local-path", os, arch))
                .text()
                .trim();
        // Verify the application is using the configured toolchain JDK
        System.out.println(runOutput.output());
        assertThat(runOutput).output().contains("Java home:");
        assertThat(runOutput).output().contains(compileJdkFileName.replace("\\", "/"));
    }

    @Test
    void generates_only_the_files_for_the_current_arch_and_os(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        gradle.withArgs("generateGradleJdkConfigs", "--onlyForCurrentOsArch").buildsSuccessfully();

        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        try (Stream<Path> paths = Files.find(
                rootProject.path().resolve("gradle/jdks"),
                4,
                (path, attr) -> path.getFileName().toString().equals("local-path") && attr.isRegularFile())) {
            Set<String> actualPaths = paths.map(
                            it -> rootProject.path().relativize(it).toString())
                    .collect(Collectors.toSet());
            Set<String> expectedPaths = Stream.of("11", "17", "21")
                    .map(it -> String.format("gradle/jdks/%s/%s/%s/local-path", it, os, arch))
                    .collect(Collectors.toSet());
            assertThat(actualPaths).isEqualTo(expectedPaths);
        }
    }

    @Test
    @SuppressWarnings("GradleTestPluginsBlock")
    void javaToolchains_correctly_set_up_with_baseline_java(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
                distributionTarget = '17_PREVIEW'
            }

            tasks.register("printGradleHome") {
                doLast {
                    println "java.home: " + System.getProperty("java.home")
                }
            }
            """);

        SubProject subprojectLib21 = rootProject.subproject("subproject-lib-21");
        subprojectLib21.buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
               target = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(getMainJavaCode());

        SubProject subprojectLib11 = rootProject.subproject("subproject-lib-11");
        subprojectLib11.buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
                library()
            }
            """);
        subprojectLib11.mainSourceSet().java().writeClass(getMainJavaCode());

        InvocationResult gradleHomeOutput = gradle.withArgs("printGradleHome").buildsSuccessfully();

        String os = OperatingSystem.get().uiName();
        String arch = CurrentArch.get().uiName();
        String daemonJdkFileName = rootProject
                .file(String.format(
                        "gradle/jdks/%s/%s/%s/local-path", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17, os, arch))
                .text()
                .trim();
        // Verify the daemon is using the configured JDK
        assertThat(gradleHomeOutput).output().contains("java.home:");
        assertThat(gradleHomeOutput).output().contains(daemonJdkFileName.replace("\\", "/"));

        Set<String> jdkVersions = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions).isEqualTo(Set.of("11", "17", "21"));

        gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .file("classes/java/main/Main.class")
                .path()
                .toFile();
        assertThat(readBytecodeVersion(compiledClass)).isEqualTo(Pair.of(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE));

        File subproject11Class = subprojectLib11
                .buildDir()
                .file("classes/java/main/Main.class")
                .path()
                .toFile();
        assertThat(readBytecodeVersion(subproject11Class)).isEqualTo(Pair.of(0, JAVA_11_BYTECODE));

        File subproject21Class = subprojectLib21
                .buildDir()
                .file("classes/java/main/Main.class")
                .path()
                .toFile();
        assertThat(readBytecodeVersion(subproject21Class)).isEqualTo(Pair.of(0, JAVA_21_BYTECODE));
    }

    @Test
    void graal_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '23'
            }

            jdks {
                jdk(23) {
                    distribution = 'graalvm-ce'
                    jdkVersion = '23.0.1'
                }
            }
            """);

        Set<String> jdkVersions = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions).isEqualTo(Set.of(GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17, "23"));

        gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .file("classes/java/main/Main.class")
                .path()
                .toFile();
        assertThat(readBytecodeVersion(compiledClass)).isEqualTo(Pair.of(0, JAVA_23_BYTECODE));
    }

    @Test
    void only_generates_daemon_jdk(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.buildGradle().append("""
            jdks {
                daemonJdkOnly()
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
            boolean allMatch = paths.allMatch(
                    it -> it.endsWith(String.format("gradle/jdks/%s", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17)));
            assertThat(allMatch).isTrue();
        }
    }

    @Test
    void can_bump_java_major_version_when_baseline_java_is_applied(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(getMainJavaCode());

        gradle.withArgs("generateGradleJdkConfigs").buildsSuccessfully();

        Set<String> jdkVersions1 = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions1).isEqualTo(Set.of("11", "17"));

        gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21")
                .buildsSuccessfully();

        Set<String> jdkVersions2 = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions2).isEqualTo(Set.of("11", "17", "21"));

        InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();

        assertThat(failingCheck).output().contains("Unexpected Java versions configured: [21]");

        gradle.withArgs("setupJdks", "compileJava").buildsSuccessfully();

        Set<String> jdkVersions3 = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions3).isEqualTo(Set.of("11", GradleJdkTestUtils.DAEMON_MAJOR_VERSION_17));

        gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();

        Set<String> jdkVersions4 = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions4).isEqualTo(Set.of("11", "17", "21"));
    }

    @Test
    void only_jdk_versions_to_use_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            jdks {
                jdkMajorVersionsToUse = ["17", "21"]
            }
            """);

        gradle.withArgs("setupJdks").buildsSuccessfully();

        Set<String> jdkVersions = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions).isEqualTo(Set.of("17", "21"));
    }

    @Test
    @SuppressWarnings("GradleTestPluginsBlock")
    void only_required_java_versions_are_configured(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '17'
            }
            """);

        SubProject subprojectLib21 = rootProject.subproject("subproject-lib-21");
        subprojectLib21.buildGradle().append("""
            apply plugin: 'java-library'
            javaVersion {
               target = 17
               runtime = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(getMainJavaCode());

        Set<String> jdkVersions = listJdkVersionDirectories(rootProject.path().resolve("gradle/jdks"));
        assertThat(jdkVersions).isEqualTo(Set.of("17", "21"));
    }

    @Test
    void fails_if_the_jdk_version_is_not_configured_7_6_4(GradleInvoker gradle, RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject);
        applyBaselineJavaVersions(rootProject);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 15
            }
            """);
        rootProject.mainSourceSet().java().writeClass(getMainJavaCode());
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("No compatible toolchains found for request specification: {languageVersion=15, vendor=any,"
                        + " implementation=vendor-specific} (auto-detect false, auto-download false).");
    }
}
