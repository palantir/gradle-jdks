/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks.testing;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

final class GradleWithJdksStore {
    private static final Namespace NAMESPACE = Namespace.create(GradleWithJdksStore.class);
    private static final String KEY = "gradleJdksStore";

    public static boolean hasRunJdksSetup(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrDefault(KEY, Boolean.class, false);
    }

    public static void setHasRun(ExtensionContext context) {
        context.getStore(NAMESPACE).put(KEY, true);
    }

    private GradleWithJdksStore() {}
}
