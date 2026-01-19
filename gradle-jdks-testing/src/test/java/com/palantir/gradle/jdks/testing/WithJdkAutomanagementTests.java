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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DecoratorContext;
import com.palantir.gradle.testing.junit.RegistersGradleInvokerDecorator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link WithJdkAutomanagement} annotation and its decorator factory.
 */
class WithJdkAutomanagementTests {

    @Test
    void annotation_has_registers_decorator_meta_annotation() {
        RegistersGradleInvokerDecorator metaAnnotation =
                WithJdkAutomanagement.class.getAnnotation(RegistersGradleInvokerDecorator.class);
        assertThat(metaAnnotation).isNotNull();
        assertThat(metaAnnotation.value()).isEqualTo(JdkAutomanagementDecoratorFactory.class);
    }

    @Test
    void factory_creates_decorator() {
        JdkAutomanagementDecoratorFactory factory = new JdkAutomanagementDecoratorFactory();
        WithJdkAutomanagement annotation = AnnotatedClass.class.getAnnotation(WithJdkAutomanagement.class);

        assertThat(factory.create(annotation)).isInstanceOf(JdkAutomanagementDecorator.class);
    }

    @Test
    void decorator_wraps_invoker_with_jdk_invoker() {
        JdkAutomanagementDecorator decorator = new JdkAutomanagementDecorator();
        GradleInvoker mockDelegate = _args -> null;
        DecoratorContext context = new DecoratorContext(Path.of("/tmp/test"), null, null);

        GradleInvoker result = decorator.decorate(context, mockDelegate);

        assertThat(result).isInstanceOf(GradleWithJdksInvoker.class);
    }

    @WithJdkAutomanagement
    private static final class AnnotatedClass {}
}
