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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.jdks.AzulZuluJdkDistribution.ZuluVersionSplit;
import org.junit.jupiter.api.Test;

class AzulZuluJdkDistributionTest {
    @Test
    void zulu_version_combination_survives_roundtrip() {
        ZuluVersionSplit versionSplit = AzulZuluJdkDistribution.splitCombinedVersion(
                ZuluVersionUtils.combineZuluVersions("11.22.33", "11.0.1"));
        assertThat(versionSplit.javaVersion()).isEqualTo("11.0.1");
        assertThat(versionSplit.zuluVersion()).isEqualTo("11.22.33");
    }
}
