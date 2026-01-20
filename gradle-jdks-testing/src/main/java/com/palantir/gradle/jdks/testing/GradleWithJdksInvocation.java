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

import com.palantir.gradle.testing.execution.GradleInvocation;
import com.palantir.gradle.testing.execution.InvocationResult;
import java.util.concurrent.Callable;

/**
 * A GradleInvocation that runs JDK setup before executing the actual build.
 */
record GradleWithJdksInvocation(GradleInvocation setupInvocation, Callable<GradleInvocation> tasksInvocation)
        implements GradleInvocation {

    @Override
    public InvocationResult buildsSuccessfully() {
        setupJdkAutomanagement();
        try {
            return tasksInvocation.call().buildsSuccessfully();
        } catch (Exception e) {
            throw new RuntimeException("Failed to run the gradle invoker", e);
        }
    }

    @Override
    public InvocationResult buildsWithFailure() {
        setupJdkAutomanagement();
        try {
            return tasksInvocation.call().buildsWithFailure();
        } catch (Exception e) {
            throw new RuntimeException("Failed to run the gradle invoker", e);
        }
    }

    private void setupJdkAutomanagement() {
        try {
            setupInvocation.buildsSuccessfully();
        } catch (Exception e) {
            throw new JdkSetupFailureException(e);
        }
    }
}
