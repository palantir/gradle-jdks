/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.jdks;

import java.util.Map;
import java.util.Optional;

final class JdkDistributions {
    private static final Map<JdkDistributionName, JdkDistribution> JDK_DISTRIBUTIONS = Map.of(
            JdkDistributionName.AZUL_ZULU,
            new AzulZuluJdkDistribution(),
            JdkDistributionName.AMAZON_CORRETTO,
            new AmazonCorrettoJdkDistribution());

    public JdkDistribution get(JdkDistributionName jdkDistributionName) {
        return Optional.ofNullable(JDK_DISTRIBUTIONS.get(jdkDistributionName))
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Could not find JDK distribution %s. Available: %s",
                        jdkDistributionName, JDK_DISTRIBUTIONS.keySet())));
    }
}
