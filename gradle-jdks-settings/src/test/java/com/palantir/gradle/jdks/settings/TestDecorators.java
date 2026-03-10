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

package com.palantir.gradle.jdks.settings;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DecoratorContext;
import com.palantir.gradle.testing.junit.GradleInvokerDecorator;
import com.palantir.gradle.testing.junit.RegistersGradleInvokerDecorator;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

public final class TestDecorators {
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @RegistersGradleInvokerDecorator(CustomGradleUserHomeDecorator.class)
    public @interface WithGradleUserHomeInBuildDir {}

    public static final class CustomGradleUserHomeDecorator
            implements GradleInvokerDecorator<WithGradleUserHomeInBuildDir> {

        @Override
        public GradleInvoker decorate(
                DecoratorContext context, GradleInvoker invoker, List<WithGradleUserHomeInBuildDir> _annotations) {
            return options -> invoker.with(options.asBuilder()
                    .customGradleUserHome(
                            context.rootProject().buildDir().path().resolve("tmp"))
                    .build());
        }
    }

    private TestDecorators() {}
}
