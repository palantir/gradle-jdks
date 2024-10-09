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

import com.google.common.base.Suppliers;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.ide.progress.TaskCancellation;
import com.intellij.platform.ide.progress.TasksKt;
import com.intellij.platform.util.progress.StepsKt;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

@Service(Service.Level.PROJECT)
public final class GradleJdksProjectService implements Disposable {

    private final Logger logger = Logger.getInstance(GradleJdksProjectService.class);
    private static final String TOOL_WINDOW_NAME = "Gradle JDK Setup";

    private final Project project;
    private final Supplier<ConsoleView> consoleView = Suppliers.memoize(this::initConsoleView);

    public GradleJdksProjectService(Project project) {
        this.project = project;
    }

    private ConsoleView initConsoleView() {
        ConsoleView newConsoleView =
                TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.invokeLater(() -> {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_NAME);
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_NAME, true, ToolWindowAnchor.BOTTOM);
            }
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(newConsoleView.getComponent(), "", false);
            toolWindow.getContentManager().addContent(content);
        });

        return newConsoleView;
    }

    public void focusOnWindow() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.invokeLater(() -> {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_NAME);
            if (toolWindow != null) {
                toolWindow.activate(null, true, false);
            }
        });
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
                TaskCancellation.nonCancellable(),
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
            consoleView.get().clear();
            GeneralCommandLine cli =
                    new GeneralCommandLine("./gradle/gradle-jdks-setup.sh").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {

                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
                    updateGradleJvm();
                }
            });
            consoleView.get().attachToProcess(handler);
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
                focusOnWindow();
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to setup Gradle JDKs for Intellij", e);
        }
    }

    private void updateGradleJvm() {
        for (GradleProjectSettings projectSettings :
                GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            File gradleConfigFile = Path.of(projectSettings.getExternalProjectPath(), ".gradle/config.properties")
                    .toFile();
            if (!gradleConfigFile.exists()) {
                consoleView
                        .get()
                        .print(
                                "Skipping gradleJvm Configuration because no value was configured in"
                                        + " `.gradle/config.properties`",
                                ConsoleViewContentType.LOG_INFO_OUTPUT);

                continue;
            }

            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(gradleConfigFile));
                Optional<String> javaHome = Optional.ofNullable(properties.getProperty("java.home"));
                if (javaHome.isPresent()) {
                    String sdkName = getSdkName(javaHome.get());
                    WriteAction.runAndWait(() -> {
                        Sdk sdk = Optional.ofNullable(
                                        ProjectJdkTable.getInstance().findJdk(sdkName))
                                .orElseGet(() -> {
                                    Sdk newSdk = JavaSdk.getInstance().createJdk(sdkName, javaHome.get(), false);
                                    ProjectJdkTable.getInstance().addJdk(newSdk);
                                    return newSdk;
                                });
                        ProjectRootManager.getInstance(project).setProjectSdk(sdk);
                        projectSettings.setGradleJvm(ExternalSystemJdkUtil.USE_PROJECT_JDK);
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to set gradleJvm", e);
            }
        }
    }

    private String getSdkName(String javaHomePath) {
        return String.format(
                "gradle-jdks-%s", Path.of(javaHomePath).getFileName().toString());
    }

    @Override
    public void dispose() {
        ConsoleView view = consoleView.get();
        if (view != null) {
            view.dispose();
        }
    }
}
