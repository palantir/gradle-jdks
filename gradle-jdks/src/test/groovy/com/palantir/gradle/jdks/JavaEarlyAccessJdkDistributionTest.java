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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.palantir.gradle.jdks.JavaEarlyAccessJdkDistribution.EarlyAccessVersion;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class JavaEarlyAccessJdkDistributionTest {

    private static final class TestData {
        String version;
        String expectedPathTemplate;

        TestData(String version, String expectedPathTemplate) {
            this.version = version;
            this.expectedPathTemplate = expectedPathTemplate;
        }
    }

    private static final TestData TEST_JDK24_EA = new TestData("24-ea+17", "jdk24/17/GPL/openjdk-24-ea+17_%s-%s_bin");
    private static final TestData TEST_LOOM_EA = new TestData("24-loom+7-60", "loom/7/openjdk-24-loom+7-60_%s-%s_bin");
    private static final TestData TEST_VALHALLA_EA =
            new TestData("23-valhalla+1-90", "valhalla/1/openjdk-23-valhalla+1-90_%s-%s_bin");

    private static final List<TestData> TEST_DATA = List.of(TEST_JDK24_EA, TEST_LOOM_EA, TEST_VALHALLA_EA);

    private final JavaEarlyAccessJdkDistribution distribution = new JavaEarlyAccessJdkDistribution();

    @TestFactory
    public Stream<DynamicTest> jdk_path_linux_x86_64() {
        return TEST_DATA.stream()
                .map(testData -> dynamicTest(testData.version, () -> {
                    JdkPath path = distribution.path(JdkRelease.builder()
                            .arch(Arch.X86_64)
                            .os(Os.LINUX_GLIBC)
                            .version(testData.version)
                            .build());
                    String expectedPath = String.format(testData.expectedPathTemplate, "linux", "x64");
                    assertThat(path.filename()).isEqualTo(expectedPath);
                    assertThat(path.extension()).isEqualTo(Extension.TARGZ);
                }));
    }

    @TestFactory
    public Stream<DynamicTest> jdk_path_linux_aarch64() {
        return TEST_DATA.stream()
                .map(testData -> dynamicTest(testData.version, () -> {
                    JdkPath path = distribution.path(JdkRelease.builder()
                            .arch(Arch.AARCH64)
                            .os(Os.LINUX_GLIBC)
                            .version(testData.version)
                            .build());
                    String expectedPath = String.format(testData.expectedPathTemplate, "linux", "aarch64");
                    assertThat(path.filename()).isEqualTo(expectedPath);
                    assertThat(path.extension()).isEqualTo(Extension.TARGZ);
                }));
    }

    @TestFactory
    public Stream<DynamicTest> jdk_path_macos_x86_64() {
        return TEST_DATA.stream()
                .map(testData -> dynamicTest(testData.version, () -> {
                    JdkPath path = distribution.path(JdkRelease.builder()
                            .arch(Arch.X86_64)
                            .os(Os.MACOS)
                            .version(testData.version)
                            .build());
                    String expectedPath = String.format(testData.expectedPathTemplate, "macos", "x64");
                    assertThat(path.filename()).isEqualTo(expectedPath);
                    assertThat(path.extension()).isEqualTo(Extension.TARGZ);
                }));
    }

    @TestFactory
    public Stream<DynamicTest> jdk_path_macos_aarch64() {
        return TEST_DATA.stream()
                .map(testData -> dynamicTest(testData.version, () -> {
                    JdkPath path = distribution.path(JdkRelease.builder()
                            .arch(Arch.AARCH64)
                            .os(Os.MACOS)
                            .version(testData.version)
                            .build());
                    String expectedPath = String.format(testData.expectedPathTemplate, "macos", "aarch64");
                    assertThat(path.filename()).isEqualTo(expectedPath);
                    assertThat(path.extension()).isEqualTo(Extension.TARGZ);
                }));
    }

    @TestFactory
    public Stream<DynamicTest> jdk_path_windows_x86_64() {
        return TEST_DATA.stream()
                .map(testData -> dynamicTest(testData.version, () -> {
                    JdkPath path = distribution.path(JdkRelease.builder()
                            .arch(Arch.X86_64)
                            .os(Os.WINDOWS)
                            .version(testData.version)
                            .build());
                    String expectedPath = String.format(testData.expectedPathTemplate, "windows", "x64");
                    assertThat(path.filename()).isEqualTo(expectedPath);
                    assertThat(path.extension()).isEqualTo(Extension.ZIP);
                }));
    }

    @Test
    public void throws_on_x86() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.X86)
                        .os(Os.LINUX_GLIBC)
                        .version(TEST_LOOM_EA.version)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void throws_on_linux_musl() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.AARCH64)
                        .os(Os.LINUX_MUSL)
                        .version(TEST_LOOM_EA.version)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void throws_on_windows_and_aarch64() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.AARCH64)
                        .os(Os.WINDOWS)
                        .version(TEST_LOOM_EA.version)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void successfully_parses_ea_version_with_major_version_only() {
        String fullVersion = "24-foo+123";
        EarlyAccessVersion eaVersion = JavaEarlyAccessJdkDistribution.parseEarlyAccessVersion(fullVersion);

        assertThat(eaVersion.projectName()).isEqualTo("foo");
        assertThat(eaVersion.javaMajorVersion()).isEqualTo("24-foo");
        assertThat(eaVersion.earlyAccessMajorVersion()).isEqualTo("123");
        assertThat(eaVersion.earlyAccessMinorVersion()).isEmpty();
        assertThat(eaVersion.fullJdkVersion()).isEqualTo(fullVersion);
    }

    @Test
    public void successfully_parses_ea_version_with_major_and_minor_version() {
        String fullVersion = "24-foo+123-456";
        EarlyAccessVersion eaVersion = JavaEarlyAccessJdkDistribution.parseEarlyAccessVersion(fullVersion);

        assertThat(eaVersion.projectName()).isEqualTo("foo");
        assertThat(eaVersion.javaMajorVersion()).isEqualTo("24-foo");
        assertThat(eaVersion.earlyAccessMajorVersion()).isEqualTo("123");
        assertThat(eaVersion.earlyAccessMinorVersion()).isPresent().contains("456");
        assertThat(eaVersion.fullJdkVersion()).isEqualTo(fullVersion);
    }

    @Test
    public void successfully_parses_jdk24_ea_project_name_special_case() {
        EarlyAccessVersion eaVersion = JavaEarlyAccessJdkDistribution.parseEarlyAccessVersion(TEST_JDK24_EA.version);

        assertThat(eaVersion.projectName()).isEqualTo("jdk24");
        assertThat(eaVersion.javaMajorVersion()).isEqualTo("24-ea");
    }

    @Test
    public void throws_on_bad_ea_version() {
        String badVersion1 = "hello";
        assertThatThrownBy(() -> JavaEarlyAccessJdkDistribution.parseEarlyAccessVersion(badVersion1))
                .isInstanceOf(IllegalArgumentException.class);

        String badVersion2 = "24-ea-1-3";
        assertThatThrownBy(() -> JavaEarlyAccessJdkDistribution.parseEarlyAccessVersion(badVersion2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
