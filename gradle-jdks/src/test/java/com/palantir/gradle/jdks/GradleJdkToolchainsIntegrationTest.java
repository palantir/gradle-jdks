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

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.junit.WithJdkAutomanagement;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@WithJdkAutomanagement
@DisabledConfigurationCache
class GradleJdkToolchainsIntegrationTest {

    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int JAVA_21_BYTECODE = 65;
    private static final int JAVA_23_BYTECODE = 67;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;
    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    private final String java17PreviewCode = """
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

    @BeforeEach
    void setup(RootProject rootProject) {
        setupJdksHardcodedVersions(rootProject);
    }

    GradleFile setupJdksHardcodedVersions(RootProject rootProject) {
        rootProject
                .settingsGradle()
                .prepend(
                        """
                           buildscript {
                                repositories {
                                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                                }
                                // we need to inject the classpath of the plugin under test manually. The tests call the `./gradlew`
                                // command directly in the tests (so not using the nebula-test workflow).
                                dependencies {
                                    classpath files(%s)
                                }
                            }
                        """,
                        getBuildPluginClasspathInjector().stream()
                                .map(file -> String.format("'%s'", file.getAbsolutePath()))
                                .collect(Collectors.joining(",")));
        rootProject.buildGradle().plugins().add("com.palantir.jdks.palantir-ca");

        return rootProject.buildGradle().append("""
            jdks {
               jdk(11) {
                  distribution = 'azul-zulu'
                  jdkVersion = '11.54.25-11.0.14.1'
               }

               jdk(17) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '17.0.3.6.1'
               }

               jdk(21) {
                  distribution = 'amazon-corretto'
                  jdkVersion = '21.0.2.13.1'
               }

               daemonTarget = 17
            }
            """);
    }

    private static List<File> getBuildPluginClasspathInjector() {
        return getPluginClasspathInjector(
                Path.of("../gradle-jdks-settings/build/pluginUnderTestMetadata/plugin-under-test-metadata.properties"));
    }

    private static List<File> getPluginClasspathInjector(Path path) {
        File propertiesFile = path.toFile();
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(propertiesFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load plugin classpath properties", e);
        }
        String classpath = properties.getProperty("implementation-classpath");
        return Set.of(classpath.split(File.pathSeparator)).stream()
                .map(File::new)
                .collect(Collectors.toList());
    }

    GradleFile applyApplicationPlugin(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("application");
        return rootProject.buildGradle().append("""
            application {
                mainClass = 'Main'
            }
            """);
    }

    GradleFile applyBaselineJavaVersions(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");
        return rootProject.buildGradle();
    }

    String getMainJavaCode() {
        return """
            public class Main {
                public static void main(String[] args) {
                    String javaHome = System.getProperty("java.home");
                    System.out.println("Java home: " + javaHome);
                }
            }
            """;
    }

    @Test
    void javaToolchains_correctly_set_up(GradleInvoker gradle, RootProject rootProject) {
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

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");

        InvocationResult result = gradle.withArgs("wrapper", "setupJdks").buildsSuccessfully();

        assertThat(result)
                .output()
                .contains("Auto-detection:     Disabled")
                .contains("Auto-download:      Disabled")
                .contains("JDK 11.0.14")
                .contains("JDK 17.0.3")
                .contains("JDK 21.0.2");

        // Check that output contains expected detection pattern
        assertThat(result).output().contains("org.gradle.java.installations.paths");

        InvocationResult gradleHomeResult = gradle.withArgs("printGradleHome").buildsSuccessfully();

        assertThat(gradleHomeResult).output().contains("java.home:");

        gradle.withArgs("compileJava").buildsSuccessfully();

        Path compiledClass = rootProject.buildDir().path().resolve("classes/java/main/Main.class");
        assertThat(readBytecodeVersion(compiledClass.toFile())).isEqualTo(Pair.of(0, JAVA_17_BYTECODE));

        InvocationResult runResult = gradle.withArgs("run").buildsSuccessfully();

        assertThat(runResult).output().contains("Java home:");
    }

    @Test
    void generates_only_the_files_for_the_current_arch_os(GradleInvoker gradle, RootProject rootProject) {
        applyApplicationPlugin(rootProject);
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");

        gradle.withArgs("generateGradleJdkConfigs", "--onlyForCurrentOsArch").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths = Files.find(
                rootProject.buildDir().path().resolve("gradle/jdks"),
                4,
                (path, attr) -> path.getFileName().toString().equals("local-path") && attr.isRegularFile())) {
            Set<String> foundPaths = paths.map(path ->
                            rootProject.buildDir().path().relativize(path).toString())
                    .collect(Collectors.toSet());
            // We expect paths for versions 11, 17, 21 for current OS/arch
            assertThat(foundPaths).hasSize(3);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void javaToolchains_correctly_set_up_with_baseline_java(
            GradleInvoker gradle, RootProject rootProject, SubProject subprojectLib21, SubProject subprojectLib11) {
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

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

        subprojectLib21.buildGradle().plugins().add("java-library");
        subprojectLib21.buildGradle().append("""
            javaVersion {
               target = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(getMainJavaCode());

        subprojectLib11.buildGradle().plugins().add("java-library");
        subprojectLib11.buildGradle().append("""
            javaVersion {
                library()
            }
            """);
        subprojectLib11.mainSourceSet().java().writeClass(getMainJavaCode());

        InvocationResult gradleHomeResult = gradle.withArgs("printGradleHome").buildsSuccessfully();

        assertThat(gradleHomeResult).output().contains("java.home:");

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        Path compiledClass = rootProject.buildDir().path().resolve("classes/java/main/Main.class");
        assertThat(readBytecodeVersion(compiledClass.toFile()))
                .isEqualTo(Pair.of(ENABLE_PREVIEW_BYTECODE, JAVA_17_BYTECODE));

        Path subproject11Class = subprojectLib11.buildDir().path().resolve("classes/java/main/Main.class");
        assertThat(readBytecodeVersion(subproject11Class.toFile())).isEqualTo(Pair.of(0, JAVA_11_BYTECODE));

        Path subproject21Class = subprojectLib21.buildDir().path().resolve("classes/java/main/Main.class");
        assertThat(readBytecodeVersion(subproject21Class.toFile())).isEqualTo(Pair.of(0, JAVA_21_BYTECODE));
    }

    @Test
    void graal_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) {
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

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

        gradle.withArgs("wrapper").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("17", "23"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        gradle.withArgs("compileJava", "--info").buildsSuccessfully();

        Path compiledClass = rootProject.buildDir().path().resolve("classes/java/main/Main.class");
        assertThat(readBytecodeVersion(compiledClass.toFile())).isEqualTo(Pair.of(0, JAVA_23_BYTECODE));
    }

    @Test
    void only_generates_daemon_jdk(GradleInvoker gradle, RootProject rootProject) {
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.buildGradle().append("""
            jdks {
                daemonJdkOnly()
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

        gradle.withArgs("wrapper").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            boolean allMatch = paths.allMatch(path -> path.endsWith("17"));
            assertThat(allMatch).isTrue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void can_bump_java_major_version_when_baseline_java_is_applied(GradleInvoker gradle, RootProject rootProject) {
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

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("11", "17"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21")
                .buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();

        assertThat(failingCheck).output().contains("Unexpected Java versions configured: [21]");

        gradle.withArgs("setupJdks", "compileJava").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("11", "17"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void only_jdkVersionsToUse_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) {
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

        rootProject.buildGradle().append("""
            jdks {
                jdkMajorVersionsToUse = ["17", "21"]
            }
            """);

        gradle.withArgs("setupJdks").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("17", "21"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void only_required_java_versions_are_configured(
            GradleInvoker gradle, RootProject rootProject, SubProject subprojectLib21) {
        applyBaselineJavaVersions(rootProject);
        applyApplicationPlugin(rootProject);

        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '17'
            }
            """);

        subprojectLib21.buildGradle().plugins().add("java-library");
        subprojectLib21.buildGradle().append("""
            javaVersion {
               target = 17
               runtime = 21
            }
            """);
        subprojectLib21.mainSourceSet().java().writeClass(getMainJavaCode());

        gradle.withArgs("wrapper").buildsSuccessfully();

        try (java.util.stream.Stream<Path> paths =
                Files.list(rootProject.buildDir().path().resolve("gradle/jdks"))) {
            Set<String> versions =
                    paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(versions).isEqualTo(Set.of("17", "21"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void fails_if_the_jdk_version_is_not_configured(GradleInvoker gradle, RootProject rootProject) {
        applyBaselineJavaVersions(rootProject);

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 15
            }
            """);
        rootProject.mainSourceSet().java().writeClass(getMainJavaCode());
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");

        gradle.withArgs("wrapper").buildsSuccessfully();

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("No compatible toolchains found")
                .containsAnyOf("languageVersion=15", "No matching toolchains found");
    }

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    static Pair<Integer, Integer> readBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = dis.readUnsignedShort();
            int majorBytecodeVersion = dis.readUnsignedShort();
            return Pair.of(minorBytecodeVersion, majorBytecodeVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
