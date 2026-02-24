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
import java.util.concurrent.Callable;
import org.gradle.testkit.runner.UnexpectedBuildFailure;

/**
 * A GradleInvocation that runs JDK setup before executing the actual build.
 */
record GradleWithJdksInvocation(
        GradleInvocation setupInvocation,
        Callable<GradleInvocation> javaToolchainsInvocation,
        Callable<GradleInvocation> tasksInvocation,
        Runnable markSetupComplete)
        implements GradleInvocation {

    @Override
    public InvocationResult buildsSuccessfully() {
        try {
            setupInvocation.buildsSuccessfully();
        } catch (UnexpectedBuildFailure e) {
            if (e.getMessage().contains("com.palantir.gradle.jdks.setup.JdkSetupFailureException:")) {
                throw new JdkSetupFailureException(e.getBuildResult().getOutput());
            }
            throw e;
        }
        try {
            // we expect this call to be successful if `setupInvocation` was successful.
            javaToolchainsInvocation.call().buildsSuccessfully();
        } catch (Exception e) {
            throw new JdkSetupFailureException(e);
        }
        markSetupComplete.run();

        return runSuccessfully(tasksInvocation);
    }

    @Override
    public InvocationResult buildsWithFailure() {
        try {
            setupInvocation.buildsSuccessfully();
        } catch (UnexpectedBuildFailure e) {
            return setupInvocation.buildsWithFailure();
        }
        try {
            // we expect this call to be successful if `setupInvocation` was successful.
            javaToolchainsInvocation.call().buildsSuccessfully();
        } catch (Exception e) {
            return runWithFailure(javaToolchainsInvocation);
        }
        markSetupComplete.run();

        return runWithFailure(tasksInvocation);
    }

    private InvocationResult runWithFailure(Callable<GradleInvocation> invocation) {
        return call(invocation).buildsWithFailure();
    }

    private InvocationResult runSuccessfully(Callable<GradleInvocation> invocation) {
        return call(invocation).buildsSuccessfully();
    }

    private static GradleInvocation call(Callable<GradleInvocation> invocation) {
        try {
            return invocation.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
