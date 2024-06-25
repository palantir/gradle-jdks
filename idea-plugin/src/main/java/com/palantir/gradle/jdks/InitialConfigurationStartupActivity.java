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
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class InitialConfigurationStartupActivity implements ProjectActivity {

    private static final String TOOL_WINDOW_NAME = "Gradle JDK Setup";

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> _continuation) {
        ConsoleView consoleView =
                TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        switch (CurrentOs.get()) {
            case WINDOWS:
                consoleView.print(
                        "Windows is not supported yet for Gradle Jdk setup", ConsoleViewContentType.LOG_INFO_OUTPUT);
                return null;
            case LINUX_MUSL:
            case LINUX_GLIBC:
            case MACOS:
                setupGradleJdks(project, consoleView);
                break;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_NAME);
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_NAME, true, ToolWindowAnchor.BOTTOM);
            }
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(consoleView.getComponent(), "", false);
            toolWindow.getContentManager().addContent(content);
            toolWindow.activate(null);
        });
        return null;
    }

    private static void setupGradleJdks(Project project, ConsoleView consoleView) {
        try {
            GeneralCommandLine cli =
                    new GeneralCommandLine("./gradlew", "setupJdks").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {

                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
                    GradleSettings gradleSettings = GradleSettings.getInstance(project);
                    for (GradleProjectSettings projectSettings : gradleSettings.getLinkedProjectsSettings()) {
                        File gradleConfigFile = Path.of(
                                        projectSettings.getExternalProjectPath(), ".gradle/config.properties")
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
                            if (Optional.ofNullable(properties.getProperty("java.home"))
                                    .isPresent()) {
                                projectSettings.setGradleJvm("#GRADLE_LOCAL_JAVA_HOME");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Could not read gradle.properties file", e);
                        }
                    }
                }
            });
            consoleView.attachToProcess(handler);
            ProcessTerminatedListener.attach(handler, project);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
