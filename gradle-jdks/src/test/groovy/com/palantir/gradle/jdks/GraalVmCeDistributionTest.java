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
import com.palantir.platform.OperatingSystem;
import org.junit.jupiter.api.Test;

class GraalVmCeDistributionTest {

    @Test
    void jdk_path_linux_aarch64() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(OperatingSystem.LINUX_GLIBC)
                .version("23.0.2")
                .build());
        assertThat(path.filename()).isEqualTo("jdk-23.0.2/graalvm-community-jdk-23.0.2_linux-aarch64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_macosx() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(OperatingSystem.MACOS)
                .version("23.0.2")
                .build());
        assertThat(path.filename()).isEqualTo("jdk-23.0.2/graalvm-community-jdk-23.0.2_macos-aarch64_bin");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_windows_x86_64() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(OperatingSystem.WINDOWS)
                .version("23.0.2")
                .build());
        assertThat(path.filename()).isEqualTo("jdk-23.0.2/graalvm-community-jdk-23.0.2_windows-x64_bin");
        assertThat(path.extension()).isEqualTo(Extension.ZIP);
    }
}
