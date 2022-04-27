/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

public interface JdkDistribution {
    String defaultBaseUrl();

    JdkPath path(JdkRelease jdkRelease);
}
