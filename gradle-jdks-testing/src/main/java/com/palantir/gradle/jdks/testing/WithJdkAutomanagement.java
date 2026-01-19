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

import com.palantir.gradle.testing.junit.RegistersGradleInvokerDecorator;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that enables JDK automanagement for Gradle plugin tests.
 *
 * <p>When applied to a test class or method, this annotation automatically:
 * <ol>
 *   <li>Adds the required plugins: {@code com.palantir.jdks} and {@code com.palantir.jdks.settings}</li>
 *   <li>Enables Gradle JDK Automanagement by setting {@code palantir.jdk.setup.enabled=true}</li>
 *   <li>Runs {@code setupJdks} to download and configure JDKs before the test build</li>
 *   <li>Configures the Gradle daemon to use the appropriate JDK</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>{@code
 * @GradlePluginTests
 * @WithJdkAutomanagement
 * class MyPluginTest {
 *     @Test
 *     void testWithJdks(GradleInvoker gradle, RootProject project) {
 *         project.buildGradle().append("""
 *             jdks {
 *                 daemonTarget = 21
 *             }
 *             """);
 *         gradle.withArgs("build").buildsSuccessfully();
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@RegistersGradleInvokerDecorator(JdkAutomanagementDecoratorFactory.class)
public @interface WithJdkAutomanagement {}
