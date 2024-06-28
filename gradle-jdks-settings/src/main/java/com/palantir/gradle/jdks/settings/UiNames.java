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

package com.palantir.gradle.jdks.settings;

import java.util.Locale;

/**
 * Simplified version of UiNames.
 * @see <a href="file:../gradle-jdks-setup-common/src/main/java/com/palantir/gradle/jdks/UiNames.java>UiNames.java</a>
 * We cannot depend directly on `gradle-jdks-setup-common` as it might lead to Gradle classLoader issues.
 */
final class UiNames {
    static String uiName(Enum<?> enumValue) {
        return enumValue.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private UiNames() {}
}