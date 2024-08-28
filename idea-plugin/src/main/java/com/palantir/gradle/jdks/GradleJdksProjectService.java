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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.progress.TasksKt;
import com.intellij.platform.util.progress.StepsKt;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
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

    public void maybeSetupGradleJdks() {
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
        TasksKt.withBackgroundProgress(
                project,
                "Gradle JDK Setup",
                (_coroutineScope, continuation) -> {
                    StepsKt.withProgressText(
                            "`Gradle JDK Setup` is running. Logs in the `Gradle JDK Setup` window ...",
                            (_cor, conti) -> {
                                setupGradleJdks();
                                return conti;
                            },
                            continuation);
                    return continuation;
                },
                new Continuation<>() {
                    @Override
                    public @NotNull CoroutineContext getContext() {
                        return EmptyCoroutineContext.INSTANCE;
                    }

                    @Override
                    public void resumeWith(@NotNull Object _object) {}
                });
    }

    private void setupGradleJdks() {
        try {
            GradleJdksToolWindowService toolWindowService = project.getService(GradleJdksToolWindowService.class);
            ConsoleView consoleView = toolWindowService.getConsoleView();
            consoleView.clear();
            GeneralCommandLine cli =
                    new GeneralCommandLine("./gradle/gradle-jdks-setup.sh").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {

                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
                    updateGradleJvm(consoleView);
                }
            });
            consoleView.attachToProcess(handler);
            ProcessTerminatedListener.attach(handler, project, "Gradle JDK setup finished with exit code $EXIT_CODE$");
            handler.waitFor();
            if (handler.getExitCode() != 0) {
                Notification notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup("Gradle JDK setup Notifications")
                        .createNotification(
                                "Gradle JDK setup",
                                String.format("Gradle JDK setup failed with exit code %s", handler.getExitCode()),
                                NotificationType.ERROR);
                notification.notify(project);
                notification.addAction(new NotificationAction("Show Gradle JDKs setup logs") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent _event, @NotNull Notification _notification) {
                        toolWindowService.focusOnWindow();
                    }
                });
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to setup Gradle JDKs for Intellij", e);
        }
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
