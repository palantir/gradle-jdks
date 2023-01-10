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

import com.palantir.gradle.jdks.GraalVmCeDistribution.GraalVersionSplit;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.JdkRelease.Arch;
import org.junit.jupiter.api.Test;

class GraalVmCeDistributionTest {

    @Test
    void graalvm_ce_version_splits_version() {
        GraalVersionSplit versionSplit = GraalVmCeDistribution.splitVersion("17.23.2.0");
        assertThat(versionSplit.javaVersion()).isEqualTo("17");
        assertThat(versionSplit.graalVersion()).isEqualTo("23.2.0");
    }

    @Test
    void jdk_path_linux_x86_64() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.LINUX_GLIBC)
                .version("17.22.3.0")
                .build());
        assertThat(path.filename()).isEqualTo("vm-22.3.0/graalvm-ce-java17-linux-amd64-22.3.0");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_macosx() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.AARCH64)
                .os(Os.MACOS)
                .version("19.22.3.0")
                .build());
        assertThat(path.filename()).isEqualTo("vm-22.3.0/graalvm-ce-java19-darwin-aarch64-22.3.0");
        assertThat(path.extension()).isEqualTo(Extension.TARGZ);
    }

    @Test
    void jdk_path_windows_x86_64() {
        GraalVmCeDistribution distribution = new GraalVmCeDistribution();
        JdkPath path = distribution.path(JdkRelease.builder()
                .arch(Arch.X86_64)
                .os(Os.WINDOWS)
                .version("11.20.3.6")
                .build());
        assertThat(path.filename()).isEqualTo("vm-20.3.6/graalvm-ce-java11-windows-amd64-20.3.6");
        assertThat(path.extension()).isEqualTo(Extension.ZIP);
    }
}
