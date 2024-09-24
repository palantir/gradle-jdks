package com.palantir.gradle.jdks;

import com.google.common.annotations.VisibleForTesting;
import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import org.immutables.value.Value;

public class LoomEaJdkDistribution implements JdkDistribution {
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

        String[] parts = fullVersion.split("\\+");
        if (parts.length != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+loomMajorVersion-loomMinorVersion`"
                            + "(e.g. 24-loom+7-60 == Java Major Version: 24-loom, Loom Major Version: 7, Loom Minor Version: 60)",
                    fullVersion));
        }

        String javaMajorVersion = parts[0];
        String[] loomParts = parts[1].split("-");
        if (loomParts.length != 2) {
            throw new IllegalArgumentException(String.format(
                    "Expected %s to be of the form: `javaMajorVersion+loomMajorVersion-loomMinorVersion`"
                            + "(e.g. 24-loom+7-60 == Java Major Version: 24-loom, Loom Major Version: 7, Loom Minor Version: 60)",
                    fullVersion));
        }

        return ImmutableLoomEaVersion.builder()
                .javaMajorVersion(javaMajorVersion)
                .loomMajorVersion(loomParts[0])
                .loomMinorVersion(loomParts[1])
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
