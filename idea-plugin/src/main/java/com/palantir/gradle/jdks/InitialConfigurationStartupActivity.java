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
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
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
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InitialConfigurationStartupActivity implements ProjectActivity {

    private static final String TOOL_WINDOW_NAME = "Gradle JDK Setup";
    private static final Logger log = LoggerFactory.getLogger(InitialConfigurationStartupActivity.class);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> _continuation) {
        GradleSettings gradleSettings = GradleSettings.getInstance(project);
        if (gradleSettings.getLinkedProjectsSettings().isEmpty()) {
            // noop, this is not a gradle project
            log.warn("No linked projects found, skipping Gradle JDK setup");
            return project;
        }
        if (Optional.ofNullable(project.getBasePath()).isEmpty()) {
            log.warn("Project base path is null, skipping Gradle JDK setup");
            return project;
        }
        if (!getConfiguredTasks(project).contains("ideSetup")) {
            log.warn("No `ideSetup` task was configured in the project, skipping Gradle JDK setup");
            return project;
        }
        ConsoleView consoleView =
                TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        setupGradleJdks(project, gradleSettings, consoleView);
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
        return project;
    }

    private static void setupGradleJdks(Project project, GradleSettings gradleSettings, ConsoleView consoleView) {
        try {
            GeneralCommandLine cli =
                    new GeneralCommandLine(getGradlewCommand(), "ideSetup").withWorkDirectory(project.getBasePath());
            OSProcessHandler handler = new OSProcessHandler(cli);
            handler.startNotify();
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent _event) {
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
                                ExternalSystemUtil.refreshProjects(
                                        new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to set gradleJvm to #GRADLE_LOCAL_JAVA_HOME", e);
                        }
                    }
                }
            });
            consoleView.attachToProcess(handler);
            ProcessTerminatedListener.attach(handler, project);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to setup Gradle JDKs for Intellij", e);
        }
    }

    public static Set<String> getConfiguredTasks(Project project) {
        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(new File(project.getBasePath()))
                .connect()) {

            return connection.model(GradleProject.class).get().getTasks().stream()
                    .map(GradleTask::getName)
                    .collect(Collectors.toSet());
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
