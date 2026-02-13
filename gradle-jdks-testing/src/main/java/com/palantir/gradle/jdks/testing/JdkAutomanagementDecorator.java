/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DecoratorContext;
import com.palantir.gradle.testing.junit.GradleInvokerDecorator;
import java.util.List;

/**
 * Decorator that enables JDK automanagement for Gradle invocations.
 */
public final class JdkAutomanagementDecorator implements GradleInvokerDecorator<WithJdkAutomanagement> {

    @Override
    public GradleInvoker decorate(DecoratorContext context, GradleInvoker delegate, List<WithJdkAutomanagement> _annotations) {
        return new GradleWithJdksInvoker(context.extensionContext(), context.rootProject(), delegate);
    }
}
