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
import java.util.Optional;
import org.immutables.value.Value;

public final class JavaEarlyAccessJdkDistribution implements JdkDistribution {
    // example artifact uris:
    // jdk24 EA:
    // https://download.java.net/java/early_access/jdk24/17/GPL/openjdk-24-ea+17_linux-aarch64_bin.tar.gz
    // loom jdk24 EA:
    // https://download.java.net/java/early_access/loom/7/openjdk-24-loom+7-60_linux-aarch64_bin.tar.gz
    // valhall jdk24 EA:
    // https://download.java.net/java/early_access/valhalla/1/openjdk-23-valhalla+1-90_linux-aarch64_bin.tar.gz

    @Override
    public String defaultBaseUrl() {
        return "https://download.java.net/java/early_access";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        EarlyAccessVersion eaVersion = parseEarlyAccessVersion(jdkRelease.version());

        String pathPrefix = String.format("%s/%s", eaVersion.projectName(), eaVersion.earlyAccessMajorVersion());
        if (eaVersion.projectName().equals("jdk24")) {
            // jdk24 ea paths are a bit different, so we special-case them here
            // (see also special-casing in parseEarlyAccessVersion which sets the project name to "jdk24")
            // (see above, jdk24 EA builds have a "/GPL" in the path on download.java.net)
            pathPrefix = pathPrefix + "/GPL";
        }

        String filename = String.format(
                "openjdk-%s_%s-%s_bin",
                eaVersion.fullJdkVersion(), os(jdkRelease.os()), arch(jdkRelease.arch(), jdkRelease.os()));

        String fullPath = String.format("%s/%s", pathPrefix, filename);

        return JdkPath.builder()
                .filename(fullPath)
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
    static EarlyAccessVersion parseEarlyAccessVersion(String fullVersion) {
        // some examples:
        //
        // input: "24-ea+17"
        // outputs:
        //   projectName == "jdk24" (this is a special-case)
        //   javaMajorVersion == "24-ea"
        //   earlyAccessMajorVersion == "17"
        //   earlyAccessMinorVersion == Optional.empty()
        //
        // input: "24-loom+7-60"
        // outputs:
        //   projectName == "loom"
        //   javaMajorVersion == "24-loom"
        //   earlyAccessMajorVersion == "7"
        //   earlyAccessMinorVersion == "60"

        List<String> parts = Splitter.on("+").splitToList(fullVersion);
        if (parts.size() != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+earlyAccessMajorVersion`"
                            + " or `javaMajorVersion+earlyAccessMajorVersion-earlyAccessMinorVersion`"
                            + " (e.g. 24-loom+7-60 == Java Major Version: 24-loom, Early Access Major Version: 7,"
                            + " Early Access Minor Version: 60)",
                    fullVersion));
        }

        String javaMajorVersion = parts.get(0);
        List<String> javaMajorVersionParts = Splitter.on("-").splitToList(javaMajorVersion);
        if (javaMajorVersionParts.size() != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to have javaMajorVersion of the form: `jvmVersion-projectName`"
                            + " (e.g. 24-loom+7-60 == JVM Version: 24, Project Name: loom)",
                    fullVersion));
        }

        String projectName = javaMajorVersionParts.get(1);
        // special case: for JDK24 EA builds with a version string like "24-ea+17", the project name is "jdk24"
        if (projectName.equals("ea")) {
            projectName = "jdk24";
        }

        List<String> earlyAccessParts = Splitter.on("-").splitToList(parts.get(1));
        if (earlyAccessParts.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+earlyAccessMajorVersion`"
                            + " or `javaMajorVersion+earlyAccessMajorVersion-earlyAccessMinorVersion`"
                            + " (e.g. 24-loom+7-60 == Java Major Version: 24-loom, Early Access Major Version: 7,"
                            + " Early Access Minor Version: 60)",
                    fullVersion));
        }

        ImmutableEarlyAccessVersion.Builder builder = ImmutableEarlyAccessVersion.builder()
                .projectName(projectName)
                .javaMajorVersion(parts.get(0))
                .earlyAccessMajorVersion(earlyAccessParts.get(0));
        if (earlyAccessParts.size() > 1) {
            builder.earlyAccessMinorVersion(earlyAccessParts.get(1));
        }

        return builder.build();
    }

    @Value.Immutable
    interface EarlyAccessVersion {
        String projectName();

        String javaMajorVersion();

        String earlyAccessMajorVersion();

        Optional<String> earlyAccessMinorVersion();

        @Value.Derived
        default String fullJdkVersion() {
            String minorVersionSuffix =
                    earlyAccessMinorVersion().map(v -> "-" + v).orElse("");
            return String.format("%s+%s%s", javaMajorVersion(), earlyAccessMajorVersion(), minorVersionSuffix);
        }
    }
}
