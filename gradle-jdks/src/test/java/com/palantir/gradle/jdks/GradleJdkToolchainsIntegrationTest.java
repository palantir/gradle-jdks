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

import com.palantir.gradle.testing.assertion.GradlePluginTestAssertions;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
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
        rootProject.buildGradle().plugins().add("com.palantir.jdks.palantir-ca");
        rootProject.buildGradle().append("""
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
        rootProject.buildGradle().plugins().add("application");
        rootProject.buildGradle().append("""
            application {
                mainClass = 'Main'
            }
            """);
    }

    private static String getMainJavaCode() {
        return """
            public class Main {
                public static void main(String[] args) {
                    String javaHome = System.getProperty("java.home");
                    System.out.println("Java home: " + javaHome);
                }
            }
            """;
    }

    @Nested
    @WithJdkAutomanagement
    class JavaToolchainsTests {

        @Test
        void javaToolchains_correctly_set_up(GradleInvoker gradle, RootProject rootProject) {
            rootProject.mainSourceSet().java().writeClass(getMainJavaCode());
            rootProject.buildGradle().prepend("""
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

            InvocationResult result = gradle.withArgs("javaToolchains").buildsSuccessfully();

            assertThat(result)
                    .output()
                    .contains("Auto-detection:     Disabled")
                    .contains("Auto-download:      Disabled")
                    .contains("JDK 11.0.14")
                    .contains("JDK 17.0.3")
                    .contains("JDK 21.0.2");

            // Check that output contains expected detection pattern
            assertThat(result).output().contains("org.gradle.java.installations.paths");

            InvocationResult gradleHomeResult =
                    gradle.withArgs("printGradleHome").buildsSuccessfully();

            assertThat(gradleHomeResult).output().contains("java.home:");

            gradle.withArgs("compileJava").buildsSuccessfully();

            Path compiledClass = rootProject.buildDir().path().resolve("classes/java/main/Main.class");
            assertThat(readBytecodeVersion(compiledClass.toFile())).isEqualTo(Pair.of(0, JAVA_17_BYTECODE));

            InvocationResult runResult = gradle.withArgs("run").buildsSuccessfully();

            assertThat(runResult).output().contains("Java home:");
        }

        @Test
        void only_jdkVersionsToUse_jdks_are_generated(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().plugins().add("application");
            rootProject.buildGradle().append("""
                application {
                    mainClass = 'Main'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

            rootProject.buildGradle().append("""
                jdks {
                    jdkMajorVersionsToUse = ["17", "21"]
                }
                """);

            gradle.withArgs("setupJdks").buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("17", "21"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    @WithJdkAutomanagement
    class BaselineJavaVersionsTests {

        @BeforeEach
        void setup(RootProject rootProject) {
            rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");
            rootProject.buildGradle().plugins().add("application");
            rootProject.buildGradle().append("""
                application {
                    mainClass = 'Main'
                }
                """);
        }

        @Test
        void javaToolchains_correctly_set_up_with_baseline_java(
                GradleInvoker gradle, RootProject rootProject, SubProject subprojectLib21, SubProject subprojectLib11) {
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

            InvocationResult gradleHomeResult =
                    gradle.withArgs("printGradleHome").buildsSuccessfully();

            assertThat(gradleHomeResult).output().contains("java.home:");

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            gradle.withArgs("compileJava").buildsSuccessfully();

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

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("17", "23"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            gradle.withArgs("compileJava").buildsSuccessfully();

            Path compiledClass = rootProject.buildDir().path().resolve("classes/java/main/Main.class");
            assertThat(readBytecodeVersion(compiledClass.toFile())).isEqualTo(Pair.of(0, JAVA_23_BYTECODE));
        }

        @Test
        void only_generates_daemon_jdk(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                jdks {
                    daemonJdkOnly()
                }
                """);

            rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
            rootProject.mainSourceSet().java().writeClass(java17PreviewCode);

            gradle.withArgs("wrapper").buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                boolean allMatch = paths.allMatch(path -> path.endsWith("17"));
                assertThat(allMatch).isTrue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Test
        void only_required_java_versions_are_configured(
                GradleInvoker gradle, RootProject rootProject, SubProject subprojectLib21) {
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

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("17", "21"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Test
        void fails_if_the_jdk_version_is_not_configured(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 15
                }
                """);
            rootProject.mainSourceSet().java().writeClass(getMainJavaCode());

            InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

            assertThat(result)
                    .output()
                    .contains("Cannot find a Java installation on your machine")
                    .contains("{languageVersion=15, vendor=any vendor, implementation=vendor-specific,"
                            + " nativeImageCapable=false}")
                    .contains("Toolchain auto-provisioning is not enabled.");
        }
    }

    @Nested
    class GenerateGradleJdkConfigsTest {

        @Test
        void can_bump_java_major_version_when_baseline_java_is_applied(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().plugins().add("java").add("com.palantir.jdks");
            rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
            rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");

            rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = '11'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(getMainJavaCode());

            gradle.withArgs("wrapper", "generateGradleJdkConfigs").buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("11", "17"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            gradle.withArgs("generateGradleJdkConfigs", "--includeVersion=11", "--includeVersion=21")
                    .buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            InvocationResult failingCheck = gradle.withArgs("check").buildsWithFailure();

            GradlePluginTestAssertions.assertThat(failingCheck)
                    .output()
                    .contains("Unexpected Java versions configured: [21]");

            gradle.withArgs("setupJdks", "-x", "runJavaToolchains", "compileJava")
                    .buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("11", "17"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            gradle.withArgs("generateGradleJdkConfigs", "--includeAllJdks").buildsSuccessfully();

            try (Stream<Path> paths = Files.list(rootProject.path().resolve("gradle/jdks"))) {
                Set<String> versions =
                        paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertThat(versions).isEqualTo(Set.of("11", "17", "21"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
