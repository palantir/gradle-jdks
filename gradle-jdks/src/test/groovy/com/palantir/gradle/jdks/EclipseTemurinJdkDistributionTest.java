/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import org.junit.jupiter.api.Test;

class EclipseTemurinJdkDistributionTest {
    @Test
    void jdk_path_musl_linux_x64_64() {
        EclipseTemurinJdkDistribution distribution = new EclipseTemurinJdkDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.LINUX_MUSL)
                .version("21.0.7+6")
                .build());
        assertThat(path.filename())
                .isEqualTo("temurin21-binaries/releases/download/"
                        + "jdk-21.0.7+6/OpenJDK21U-jdk_x64_alpine-linux_hotspot_21.0.7_6");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_macosx() {
        EclipseTemurinJdkDistribution distribution = new EclipseTemurinJdkDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(Os.MACOS)
                .version("17.0.15+6")
                .build());
        assertThat(path.filename())
                .isEqualTo("temurin17-binaries/releases/download/"
                        + "jdk-17.0.15+6/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.15_6");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_windows_x86_64() {
        EclipseTemurinJdkDistribution distribution = new EclipseTemurinJdkDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.WINDOWS)
                .version("11.0.27+6")
                .build());
        assertThat(path.filename())
                .isEqualTo("temurin11-binaries/releases/download/"
                        + "jdk-11.0.27+6/OpenJDK11U-jdk_x64_windows_hotspot_11.0.27_6");
        assertThat(path.extension()).isEqualTo(Extension.ZIP);
    }
}
