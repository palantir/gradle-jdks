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

import java.util.Map;
import java.util.Optional;

final class JdkDistributions {
    private static final Map<JdkDistributionName, JdkDistribution> JDK_DISTRIBUTIONS = Map.of(
            JdkDistributionName.AZUL_ZULU,
            new AzulZuluJdkDistribution(),
            JdkDistributionName.AMAZON_CORRETTO,
            new AmazonCorrettoJdkDistribution(),
            JdkDistributionName.GRAALVM_CE,
            new GraalVmCeDistribution(),
            JdkDistributionName.LOOM_EA,
            new LoomEaJdkDistribution());

    public JdkDistribution get(JdkDistributionName jdkDistributionName) {
        return Optional.ofNullable(JDK_DISTRIBUTIONS.get(jdkDistributionName))
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Could not find JDK distribution %s. Available: %s",
                        jdkDistributionName, JDK_DISTRIBUTIONS.keySet())));
    }
}
