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

import com.palantir.gradle.jdks.setup.JdkSetupFailureException;
import com.palantir.gradle.testing.execution.GradleInvocation;
import com.palantir.gradle.testing.execution.InvocationResult;
import java.util.function.Supplier;
import org.gradle.testkit.runner.UnexpectedBuildFailure;

/**
 * A GradleInvocation that runs JDK setup before executing the actual build.
 */
record GradleWithJdksInvocation(
        GradleInvocation setupInvocation, Supplier<GradleInvocation> tasksInvocation, Runnable markSetupComplete)
        implements GradleInvocation {

    @Override
    public InvocationResult buildsSuccessfully() {
        try {
            setupInvocation.buildsSuccessfully();
            markSetupComplete.run();
        } catch (UnexpectedBuildFailure e) {
            if (e.getMessage().contains("com.palantir.gradle.jdks.setup.JdkSetupFailureException:")) {
                throw new JdkSetupFailureException(e.getBuildResult().getOutput());
            }
            throw e;
        }
        return tasksInvocation.get().buildsSuccessfully();
    }

    @Override
    public InvocationResult buildsWithFailure() {
        try {
            setupInvocation.buildsSuccessfully();
            markSetupComplete.run();
        } catch (UnexpectedBuildFailure e) {
            if (e.getMessage().contains("com.palantir.gradle.jdks.setup.JdkSetupFailureException:")) {
                throw new JdkSetupFailureException(e.getBuildResult().getOutput());
            }
            return InvocationResult.from(e);
        }
        return tasksInvocation.get().buildsWithFailure();
    }
}
