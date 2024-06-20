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

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class InitialConfigurationStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ConsoleView consoleView = TextConsoleBuilderFactory.getInstance()
                    .createBuilder(project)
                    .getConsole();
            // Get the ToolWindowManager and get your ToolWindow
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow = toolWindowManager.getToolWindow("MyToolWindow");

            // If ToolWindow is not available, then you need to register it first
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow("MyToolWindow", true, ToolWindowAnchor.BOTTOM);
            }

            // Create content for ToolWindow
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(consoleView.getComponent(), "", false);
            toolWindow.getContentManager().addContent(content);
            toolWindow.activate(null);
            consoleView.print("Started running IntelliJGradleJdkSetup\n", ConsoleViewContentType.NORMAL_OUTPUT);
            String projectBasePath = project.getBasePath();
            String jarFilePath = projectBasePath + "/gradle/gradle-jdks-setup.jar";
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            try (URLClassLoader classLoader =
                    new URLClassLoader(new URL[] {new File(jarFilePath).toURI().toURL()})) {
                Class<?> loadedClass = classLoader.loadClass("com.palantir.gradle.jdks.setup.IntelijGradleJdkSetup");
                Method mainMethod = loadedClass.getMethod("main", String[].class);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ByteArrayOutputStream baerr = new ByteArrayOutputStream();
                PrintStream newOut = new PrintStream(baos);
                PrintStream newErr = new PrintStream(baerr);

                // Redirect System.out to newOut
                System.setOut(newOut);
                System.setErr(newErr);

                mainMethod.invoke(null, (Object) new String[] {projectBasePath});

                newOut.flush();
                newErr.flush();

                // The output collected from the main method
                String output = baos.toString(StandardCharsets.UTF_8);
                String err = baerr.toString(StandardCharsets.UTF_8);
                consoleView.print(output + err, ConsoleViewContentType.NORMAL_OUTPUT);

            } catch (IOException
                    | ClassNotFoundException
                    | InvocationTargetException
                    | IllegalAccessException
                    | NoSuchMethodException e) {
                throw new RuntimeException("Failed to run IntelliJGradleJdkSetup", e);
            } finally {
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
            consoleView.print("Finished running IntelliJGradleJdkSetup\n", ConsoleViewContentType.NORMAL_OUTPUT);
        });

        GradleSettings gradleSettings = GradleSettings.getInstance(project);

        // Iterate through all linked projects
        for (GradleProjectSettings projectSettings : gradleSettings.getLinkedProjectsSettings()) {
            Path maybeGradleConfigs = Path.of(projectSettings.getExternalProjectPath(), ".gradle/config.properties");
            if (maybeGradleConfigs.toFile().exists()) {
                projectSettings.setGradleJvm("#GRADLE_LOCAL_JAVA_HOME");
            }
        }
    }
}
