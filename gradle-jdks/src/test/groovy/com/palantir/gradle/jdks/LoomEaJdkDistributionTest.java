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

import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.LoomEaJdkDistribution.LoomEaVersion;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import org.junit.jupiter.api.Test;

class LoomEaJdkDistributionTest {

    private static final String LOOM_EA_VERSION = "24-loom+7-60";
    private final LoomEaJdkDistribution distribution = new LoomEaJdkDistribution();

    @Test
    public void jdk_path_linux_x86_64() {
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.LINUX_GLIBC)
                .version(LOOM_EA_VERSION)
                .build());
        assertThat(path.filename()).isEqualTo("7/openjdk-24-loom+7-60_linux-x64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    public void jdk_path_linux_aarch64() {
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(Os.LINUX_GLIBC)
                .version(LOOM_EA_VERSION)
                .build());
        assertThat(path.filename()).isEqualTo("7/openjdk-24-loom+7-60_linux-aarch64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    public void jdk_path_macos_x86_64() {
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.MACOS)
                .version(LOOM_EA_VERSION)
                .build());
        assertThat(path.filename()).isEqualTo("7/openjdk-24-loom+7-60_macos-x64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    public void jdk_path_macos_aarch64() {
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(Os.MACOS)
                .version(LOOM_EA_VERSION)
                .build());
        assertThat(path.filename()).isEqualTo("7/openjdk-24-loom+7-60_macos-aarch64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    public void jdk_path_windows_x86_64() {
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.WINDOWS)
                .version(LOOM_EA_VERSION)
                .build());
        assertThat(path.filename()).isEqualTo("7/openjdk-24-loom+7-60_windows-x64_bin");
        assertThat(path.extension()).isEqualTo(Extension.ZIP);
    }

    @Test
    public void throws_on_x86() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.X86)
                        .os(Os.LINUX_GLIBC)
                        .version(LOOM_EA_VERSION)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void throws_on_linux_musl() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.AARCH64)
                        .os(Os.LINUX_MUSL)
                        .version(LOOM_EA_VERSION)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void throws_on_windows_and_aarch64() {
        assertThatThrownBy(() -> distribution.path(JdkRelease.builder()
                        .arch(Arch.AARCH64)
                        .os(Os.WINDOWS)
                        .version(LOOM_EA_VERSION)
                        .build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void successfully_parses_loom_ea_version() {
        String fullVersion = "24-loom+7-60";
        LoomEaVersion loomEaVersion = LoomEaJdkDistribution.parseLoomEaVersion(fullVersion);

        assertThat(loomEaVersion.javaMajorVersion()).isEqualTo("24-loom");
        assertThat(loomEaVersion.loomMajorVersion()).isEqualTo("7");
        assertThat(loomEaVersion.loomMinorVersion()).isEqualTo("60");
    }

    @Test
    public void throws_on_bad_loom_ea_version() {
        String badVersion1 = "hello";
        assertThatThrownBy(() -> LoomEaJdkDistribution.parseLoomEaVersion(badVersion1))
                .isInstanceOf(IllegalArgumentException.class);

        String badVersion2 = "24-loom+7.60";
        assertThatThrownBy(() -> LoomEaJdkDistribution.parseLoomEaVersion(badVersion2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
