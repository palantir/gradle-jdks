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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum JdkDistributionName {
    AZUL_ZULU,
    AMAZON_CORRETTO;

    JdkDistributionName() {}

    @Override
    public String toString() {
        return uiName();
    }

    @JsonValue
    public final String uiName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<JdkDistributionName> fromString(String distributionUiName) {
        return Arrays.stream(JdkDistributionName.values())
                .filter(jdkDistributionName -> jdkDistributionName.uiName().equals(distributionUiName))
                .findFirst();
    }

    @JsonCreator
    public static JdkDistributionName fromStringThrowing(String distributionUiName) {
        return fromString(distributionUiName)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Cannot convert %s into a %s. Options are: %s.",
                        distributionUiName,
                        JdkDistributionName.class.getSimpleName(),
                        Arrays.stream(JdkDistributionName.values())
                                .map(JdkDistributionName::uiName)
                                .collect(Collectors.toList()))));
    }
}
