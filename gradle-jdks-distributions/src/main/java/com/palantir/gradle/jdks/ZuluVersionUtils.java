/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

public final class ZuluVersionUtils {
    public static String combineZuluVersions(String zuluVersion, String javaVersion) {
        return javaVersion + "-" + zuluVersion;
    }

    private ZuluVersionUtils() {}
}
