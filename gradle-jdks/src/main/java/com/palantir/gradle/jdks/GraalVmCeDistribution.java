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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import org.immutables.value.Value;

final class GraalVmCeDistribution implements JdkDistribution {
    @Override
    public String defaultBaseUrl() {
        return "https://github.com/graalvm/graalvm-ce-builds/releases/download";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        GraalVersionSplit splitVersion = splitVersion(jdkRelease.version());

        String filename = String.format(
                "vm-%s/graalvm-ce-java%s-%s-%s-%s",
                splitVersion.graalVersion(),
                splitVersion.javaVersion(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch()),
                splitVersion.graalVersion());

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "darwin";
            case LINUX_GLIBC:
                return "linux";
            case WINDOWS:
                return "windows";
            default:
                throw new UnsupportedOperationException("Case " + os + " not implemented");
        }
    }

    private static String arch(Arch arch) {
        switch (arch) {
            case X86_64:
                return "amd64";
            case AARCH64:
                return "aarch64";
            default:
                throw new UnsupportedOperationException("Case " + arch + " not implemented");
        }
    }

    private static Extension extension(Os operatingSystem) {
        switch (operatingSystem) {
            case LINUX_GLIBC:
            case MACOS:
                return Extension.TARGZ;
            case WINDOWS:
                return Extension.ZIP;
            default:
                throw new UnsupportedOperationException("Unknown OS: " + operatingSystem);
        }
    }

    @VisibleForTesting
    static GraalVersionSplit splitVersion(String combinedVersion) {
        int splitIndex = combinedVersion.indexOf(".");

        if (splitIndex == -1) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to at least contain one dot separating the java version from graal version. "
                            + "Expected Format `javaVersion.graalVersion` (e.g. 17.21.2.0 -> Java Version: 17, "
                            + "Graal Version: 21.2.0)",
                    combinedVersion));
        }

        return GraalVersionSplit.builder()
                .graalVersion(combinedVersion.substring(splitIndex + 1))
                .javaVersion(combinedVersion.substring(0, splitIndex))
                .build();
    }

    @Value.Immutable
    interface GraalVersionSplit {
        String graalVersion();

        String javaVersion();

        static Builder builder() {
            return new Builder();
        }

        class Builder extends ImmutableGraalVersionSplit.Builder {}
    }
}
