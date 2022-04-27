/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import org.gradle.api.provider.Property;

public abstract class JdkDistributionExtension {
    public abstract Property<String> getBaseUrl();
}
