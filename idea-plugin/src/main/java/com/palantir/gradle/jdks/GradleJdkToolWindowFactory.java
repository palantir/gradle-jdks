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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class GradleJdkToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ConsoleView consoleView =
                TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(consoleView.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}