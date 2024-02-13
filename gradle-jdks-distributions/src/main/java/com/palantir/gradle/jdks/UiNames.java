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

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

final class UiNames {
    static String uiName(Enum<?> enumValue) {
        return enumValue.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static <T extends Enum<?>> Optional<T> fromString(T[] enumValues, String uiName) {
        return Arrays.stream(enumValues)
                .filter(jdkDistributionName -> uiName(jdkDistributionName).equals(uiName))
                .findFirst();
    }

    @JsonCreator
    public static <T extends Enum<?>> T fromStringThrowing(Class<T> clazz, T[] enumValues, String uiName) {
        return fromString(enumValues, uiName)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Cannot convert %s into a %s. Options are: %s.",
                        uiName,
                        clazz.getSimpleName(),
                        Arrays.stream(enumValues).map(UiNames::uiName).collect(Collectors.toList()))));
    }

    private UiNames() {}
}
