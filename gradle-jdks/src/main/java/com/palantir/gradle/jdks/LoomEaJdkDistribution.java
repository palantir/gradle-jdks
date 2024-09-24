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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.util.List;
import org.immutables.value.Value;

public final class LoomEaJdkDistribution implements JdkDistribution {
    // example:
    // https://download.java.net/java/early_access/loom/7/openjdk-24-loom+7-60_linux-aarch64_bin.tar.gz

    @Override
    public String defaultBaseUrl() {
        return "https://download.java.net/java/early_access/loom";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        LoomEaVersion loomEaVersion = parseLoomEaVersion(jdkRelease.version());

        String filename = String.format(
                "%s/openjdk-%s_%s-%s_bin",
                loomEaVersion.loomMajorVersion(),
                loomEaVersion.fullJdkVersion(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch(), jdkRelease.os()));

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "macos";
            case LINUX_GLIBC:
                return "linux";
            case WINDOWS:
                return "windows";
            default:
                throw new UnsupportedOperationException("Case " + os + " not implemented");
        }
    }

    private static String arch(Arch arch, Os os) {
        switch (arch) {
            case X86_64:
                return "x64";
            case AARCH64:
                if (os == Os.WINDOWS) {
                    throw new UnsupportedOperationException("Case " + arch + " not implemented on windows");
                }
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
    static LoomEaVersion parseLoomEaVersion(String fullVersion) {
        // for a version string like "24-loom+7-60", we'll consider:
        //   the "javaMajorVersion" to be: "24-loom"
        //   the "loomMajorVersion" to be: "7"
        //   the "loomMinorVersion" to be: "60"

        List<String> parts = Splitter.on("+").splitToList(fullVersion);
        if (parts.size() != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+loomMajorVersion-loomMinorVersion`"
                            + " (e.g. 24-loom+7-60 == Java Major Version: 24-loom, Loom Major Version: 7,"
                            + " Loom Minor Version: 60)",
                    fullVersion));
        }

        String javaMajorVersion = parts.get(0);
        List<String> loomParts = Splitter.on("-").splitToList(parts.get(1));
        if (loomParts.size() != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+loomMajorVersion-loomMinorVersion`"
                            + " (e.g. 24-loom+7-60 == Java Major Version: 24-loom, Loom Major Version: 7,"
                            + " Loom Minor Version: 60)",
                    fullVersion));
        }

        return ImmutableLoomEaVersion.builder()
                .javaMajorVersion(javaMajorVersion)
                .loomMajorVersion(loomParts.get(0))
                .loomMinorVersion(loomParts.get(1))
                .build();
    }

    @Value.Immutable
    interface LoomEaVersion {
        String javaMajorVersion();

        String loomMajorVersion();

        String loomMinorVersion();

        @Value.Derived
        default String fullJdkVersion() {
            return String.format("%s+%s-%s", javaMajorVersion(), loomMajorVersion(), loomMinorVersion());
        }
    }
}
