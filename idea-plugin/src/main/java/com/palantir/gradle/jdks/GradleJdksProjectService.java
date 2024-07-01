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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
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
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

@Service(Service.Level.PROJECT)
public final class GradleJdksProjectService {
    private static final String TOOL_WINDOW_NAME = "Gradle JDK Setup";

    private final Project project;
    private Supplier<ConsoleView> consoleView = Suppliers.memoize(this::initConsoleView);

    public GradleJdksProjectService(Project project) {
        this.project = project;
    }

    private ConsoleView initConsoleView() {
        ConsoleView newConsoleView =
                TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_NAME);
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_NAME, true, ToolWindowAnchor.BOTTOM);
            }
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(newConsoleView.getComponent(), "", false);
            toolWindow.getContentManager().addContent(content);
            // TODO: Focus only when error or takes a long time?
            toolWindow.activate(null);
        });

        return newConsoleView;
    }

    public void setupGradleJdks() {
        try {
            // TODO: Avoid calling Gradle and just call script to save time?
            // TODO: Only run if we detect a certain file (eg setup script/jar or gradle-daemon-jdk-version)?
            GeneralCommandLine cli =
                    new GeneralCommandLine(getGradlewCommand(), "ideSetup").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
                    for (GradleProjectSettings projectSettings :
                            GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
                        File gradleConfigFile = Path.of(
                                        projectSettings.getExternalProjectPath(), ".gradle/config.properties")
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
                            if (Optional.ofNullable(properties.getProperty("java.home"))
                                    .isPresent()) {
                                projectSettings.setGradleJvm("#GRADLE_LOCAL_JAVA_HOME");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to set gradleJvm to #GRADLE_LOCAL_JAVA_HOME", e);
                        }
                    }
                }
            });
            consoleView.get().attachToProcess(handler);
            ProcessTerminatedListener.attach(handler, project);
            handler.waitFor();
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to setup Gradle JDKs for Intellij", e);
        }
    }

    private static String getGradlewCommand() {
        switch (CurrentOs.get()) {
            case WINDOWS: {
                return "gradlew.bat";
            }
            case LINUX_MUSL:
            case LINUX_GLIBC:
            case MACOS: {
                return "./gradlew";
            }
        }
        throw new IllegalStateException("Unreachable code; all Os enum values should be handled");
    }
}
