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

package com.palantir.gradle.jdks.flow;

import com.google.common.base.Throwables;
import com.palantir.gradle.jdks.flow.ToolchainFlowAction.Parameters;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.jvm.toolchain.internal.NoToolchainAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ToolchainFlowAction implements FlowAction<Parameters> {

    private static final Logger log = LoggerFactory.getLogger(ToolchainFlowAction.class);

    interface Parameters extends FlowParameters {
        @Input
        Property<BuildWorkResult> getBuildResult();

        @Input
        ListProperty<String> getConfiguredJavaMajorVersions();
    }

    @Override
    public void execute(Parameters parameters) {
        parameters.getBuildResult().get().getFailure().ifPresent(failure -> {
            List<Throwable> noToolchainsAvailable = Throwables.getCausalChain(failure).stream()
                    .filter(throwable -> throwable instanceof NoToolchainAvailableException)
                    .collect(Collectors.toList());
            if (noToolchainsAvailable.isEmpty()) {
                return;
            }
            List<String> missingToolchains = noToolchainsAvailable.stream()
                    .map(exception -> {
                        Pattern pattern = Pattern.compile("languageVersion=(\\d+)");
                        Matcher matcher = pattern.matcher(exception.getMessage());
                        if (matcher.find()) {
                            return Optional.of(matcher.group(1));
                        }
                        return Optional.<String>empty();
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            String maybeMissingToolchains = missingToolchains.isEmpty()
                    ? "some language versions"
                    : String.format("the language versions=%s", missingToolchains);
            log.error(
                    "\n"
                        + "\u001B[31m****************************************************************************************************\n"
                        + "****************************************************************************************************\n"
                        + "Gradle JDK Auto-management is enabled but {} are not configured. The current configured"
                        + " major jdks are: {}.\n"
                        + "If you are trying to manually change the JDK versions used, please run the following"
                        + " steps:\n"
                        + "\t- Make sure build.gradle files only use the configured java major versions: {}\n"
                        + "\t- Run `./gradlew generateGradleJdkConfigs --includeVersion=<newJavaMajorVersion>` to"
                        + " generate the configuration files for the <newJavaMajorVersion>.\n"
                        + "\t- Update the build.gradle's java versions with the newly configured jdks\n"
                        + "****************************************************************************************************\n"
                        + "****************************************************************************************************"
                        + "\u001B[0m",
                    maybeMissingToolchains,
                    parameters.getConfiguredJavaMajorVersions().get(),
                    parameters.getConfiguredJavaMajorVersions().get());
        });
    }
}
