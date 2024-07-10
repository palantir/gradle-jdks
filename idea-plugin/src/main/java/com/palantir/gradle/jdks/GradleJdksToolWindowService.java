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
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.util.function.Supplier;

@Service(Service.Level.PROJECT)
public final class GradleJdksToolWindowService {

    static final String TOOL_WINDOW_NAME = "Gradle JDK Setup";

    private final Supplier<ConsoleView> consoleView = Suppliers.memoize(this::initConsoleView);
    private final Project project;

    public GradleJdksToolWindowService(Project project) {
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
            toolWindow.setAvailable(true);
            toolWindow.activate(null, true, false);
        });

        return newConsoleView;
    }

    public void focusOnWindow(String toolWindowId) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.invokeLater(() -> {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                toolWindow.activate(null, true, false);
            }
        });
    }

    public void hideWindow(String toolWindowId) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.invokeLater(() -> {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                toolWindow.hide(null);
            }
        });
    }

    public ConsoleView getConsoleView() {
        return consoleView.get();
    }
}
