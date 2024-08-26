/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.ide.progress.TasksKt;
import com.intellij.platform.util.progress.StepsKt;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

@Service(Service.Level.PROJECT)
public final class GradleJdksProjectService {

    private final Logger logger = Logger.getInstance(GradleJdksProjectService.class);
    private final Project project;

    public GradleJdksProjectService(Project project) {
        this.project = project;
    }

    public void maybeSetupGradleJdks(ExternalSystemTaskType type) {
        if (project.getBasePath() == null) {
            logger.warn("Skipping setupGradleJdks because project path is null");
            return;
        }
        Path gradleSetupScript = Path.of(project.getBasePath(), "gradle/gradle-jdks-setup.sh");
        if (!Files.exists(gradleSetupScript)) {
            logger.info(String.format(
                    "Skipping setupGradleJdks because gradle JDK setup is not found %s", gradleSetupScript));
            return;
        }
        Continuation<Object> cont = new Continuation<>() {
            @Override
            public @NotNull CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NotNull Object _object) {}
        };
        TasksKt.withBackgroundProgress(
                project,
                "Gradle JDK Setup",
                (_coroutineScope, continuation) -> {
                    StepsKt.withProgressText(
                            "`Gradle JDK Setup` is running. Logs in the `Gradle JDK Setup` window ...",
                            (_cor, conti) -> {
                                setupGradleJdks(type);
                                return conti;
                            },
                            continuation);
                    return continuation;
                },
                cont);
    }

    private void setupGradleJdks(ExternalSystemTaskType type) {
        try {
            GradleJdksToolWindowService toolWindowService = project.getService(GradleJdksToolWindowService.class);
            ConsoleView consoleView = toolWindowService.getConsoleView();
            consoleView.clear();
            GeneralCommandLine cli =
                    new GeneralCommandLine("./gradle/gradle-jdks-setup.sh").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {
                private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
                private static boolean hasChangedFocus = false;

                @SuppressWarnings("FutureReturnValueIgnored")
                @Override
                public void startNotified(@NotNull ProcessEvent event) {
                    executorService.schedule(
                            () -> {
                                consoleView.print("Is notified", ConsoleViewContentType.NORMAL_OUTPUT);
                                if (!event.getProcessHandler().isProcessTerminated()) {
                                    toolWindowService.focusOnWindow(GradleJdksToolWindowService.TOOL_WINDOW_NAME);
                                }
                            },
                            10,
                            TimeUnit.SECONDS);
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
                    consoleView.print("finished", ConsoleViewContentType.NORMAL_OUTPUT);
                    updateGradleJvm(consoleView);
                    executorService.shutdown();
                    if (handler.getProcess().exitValue() == 0 && hasChangedFocus) {
                        toolWindowService.hideWindow(GradleJdksToolWindowService.TOOL_WINDOW_NAME);
                        maybeGetFocusWindowId(type).ifPresent(toolWindowService::focusOnWindow);
                    }
                }
            });
            consoleView.attachToProcess(handler);
            ProcessTerminatedListener.attach(handler, project, "Gradle JDK setup finished with exit code $EXIT_CODE$");
            handler.waitFor();
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to setup Gradle JDKs for Intellij", e);
        }
    }

    private Optional<String> maybeGetFocusWindowId(ExternalSystemTaskType type) {
        if (type == ExternalSystemTaskType.EXECUTE_TASK) {
            return Optional.of(ToolWindowId.RUN);
        } else if (type == ExternalSystemTaskType.RESOLVE_PROJECT) {
            return Optional.of("Build");
        }
        return Optional.empty();
    }

    private void updateGradleJvm(ConsoleView consoleView) {
        for (GradleProjectSettings projectSettings :
                GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            File gradleConfigFile = Path.of(projectSettings.getExternalProjectPath(), ".gradle/config.properties")
                    .toFile();
            if (!gradleConfigFile.exists()) {
                consoleView.print(
                        "Skipping gradleJvm Configuration because no value was configured in"
                                + " `.gradle/config.properties`",
                        ConsoleViewContentType.LOG_INFO_OUTPUT);
                continue;
            }

            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(gradleConfigFile));
                if (Optional.ofNullable(properties.getProperty("java.home")).isPresent()) {
                    projectSettings.setGradleJvm("#GRADLE_LOCAL_JAVA_HOME");
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to set gradleJvm to #GRADLE_LOCAL_JAVA_HOME", e);
            }
        }
    }
}
