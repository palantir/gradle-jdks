/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import org.gradle.api.provider.Property;

public abstract class JdkExtension {
    // Not called `version` to avoid being interfered with by `Project#setVersion`!
    public abstract Property<String> getJdkVersion();

    public abstract Property<JdkDistributionName> getDistributionName();

    public final void setDistribution(JdkDistributionName jdkDistributionName) {
        getDistributionName().set(jdkDistributionName);
    }

    public final void setDistribution(String distributionName) {
        setDistribution(JdkDistributionName.fromStringThrowing(distributionName));
    }
}
