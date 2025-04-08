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
import com.google.common.collect.ImmutableList;
import com.palantir.gradle.jdks.flow.ToolchainFlowAction.Parameters;
import java.util.Arrays;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ToolchainFlowAction implements FlowAction<Parameters> {

    private static final Logger log = LoggerFactory.getLogger(ToolchainFlowAction.class);
    private static final Pattern LANGUAGE_VERSION_PATTERN = Pattern.compile("languageVersion=(\\d+)");
    private static final String ANSI_RED_COLOR = "\u001B[31m";
    private static final String ANSI_RESET_COLOR = "\u001B[0m";

    interface Parameters extends FlowParameters {
        @Input
        Property<BuildWorkResult> getBuildResult();

        @Input
        ListProperty<String> getConfiguredJavaMajorVersions();
    }

    @Override
    @SuppressWarnings("LineLength")
    public void execute(Parameters parameters) {
        parameters.getBuildResult().get().getFailure().ifPresent(failure -> {
            List<Throwable> noToolchainsAvailable = Throwables.getCausalChain(failure).stream()
                    .filter(throwable ->
                            throwable instanceof org.gradle.jvm.toolchain.internal.NoToolchainAvailableException)
                    .collect(Collectors.toList());
            if (noToolchainsAvailable.isEmpty()) {
                return;
            }
            List<String> missingToolchains = noToolchainsAvailable.stream()
                    .map(exception -> {
                        Matcher matcher = LANGUAGE_VERSION_PATTERN.matcher(exception.getMessage());
                        if (matcher.find()) {
                            return Optional.of(matcher.group(1));
                        }
                        return Optional.<String>empty();
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            String maybeMissingToolchains = missingToolchains.isEmpty()
                    ? "some java versions"
                    : String.format("the java versions=%s", missingToolchains);
            String includeVersionsOption = missingToolchains.isEmpty()
                    ? "-includeVersion=<newJavaMajorVersion>"
                    : missingToolchains.stream()
                            .map(version -> String.format("--includeVersion=%s", version))
                            .collect(Collectors.joining(" "));
            String explanation = String.format(
                    "Gradle JDK Auto-management is enabled but %s are not configured. The "
                            + "current configured versions are: %s.\n"
                            + "If you are trying to manually change the Java versions used, please follow the steps:\n"
                            + "\t- Make sure build.gradle files only use the configured java major versions: %s\n"
                            + "\t- Run `./gradlew generateGradleJdkConfigs %s` to generate the jdk configuration files.\n"
                            + "\t- Update the build.gradle's java versions with the newly configured jdks\n",
                    maybeMissingToolchains,
                    parameters.getConfiguredJavaMajorVersions().get(),
                    parameters.getConfiguredJavaMajorVersions().get(),
                    includeVersionsOption);
            int maxLineSize = Arrays.stream(explanation.split("\n"))
                    .mapToInt(String::length)
                    .max()
                    .orElseThrow(IllegalStateException::new);
            String headerFooter = "*".repeat(maxLineSize);

            log.error(String.join(
                    "\n",
                    ImmutableList.<String>builder()
                            .add(ansi(ANSI_RED_COLOR))
                            .add(headerFooter)
                            .add(explanation)
                            .add(headerFooter)
                            .add(ansi(ANSI_RESET_COLOR))
                            .build()));
        });
    }

    private static String ansi(String code) {
        return Optional.ofNullable(System.getenv("CI")).map(_ignored -> "").orElse(code);
    }
}
