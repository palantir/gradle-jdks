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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import groovy.util.Node;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.xml.sax.SAXException;

public class PalantirGradleJdkIdeaPlugin implements Plugin<Project> {

    private static final Logger logger = Logging.getLogger(ToolchainsPlugin.class);

    @Override
    public final void apply(Project rootProject) {
        Preconditions.checkState(
                rootProject == rootProject.getRootProject(),
                "May only apply PalantirGradleJdkProviderPlugin to the root project");
        rootProject.getPluginManager().withPlugin("idea", ideaPlugin -> {
            configureLegacyIdea(rootProject);
            configureIntelliJImport(rootProject);
        });
    }

    private static void configureLegacyIdea(Project project) {
        IdeaModel ideaModel = project.getExtensions().getByType(IdeaModel.class);
        ideaModel.getProject().getIpr().withXml(xmlProvider -> {
            // this block is lazy
            ConfigureJdksIdeaPluginXml.configureExternalDependencies(xmlProvider.asNode());
        });
    }

    private static void configureIntelliJImport(Project project) {
        // Note: we tried using 'org.jetbrains.gradle.plugin.idea-ext' and afterSync triggers, but these are currently
        // very hard to manage as the tasks feel disconnected from the Sync operation, and you can't remove them once
        // you've added them. For that reason, we accept that we have to resolve this configuration at
        // configuration-time, but only do it when part of an IDEA import.
        if (!Boolean.getBoolean("idea.active")) {

            return;
        }
        project.getGradle().projectsEvaluated(gradle -> {
            createOrUpdateIdeaXmlFile(
                    project.file(".idea/externalDependencies.xml"),
                    node -> ConfigureJdksIdeaPluginXml.configureExternalDependencies(node));

            // Still configure legacy idea if using intellij import
            updateIdeaXmlFileIfExists(
                    project.file(project.getName() + ".ipr"),
                    node -> ConfigureJdksIdeaPluginXml.configureExternalDependencies(node));
        });
    }

    private static void createOrUpdateIdeaXmlFile(File configurationFile, Consumer<Node> configure) {
        updateIdeaXmlFile(configurationFile, configure, true);
    }

    private static void updateIdeaXmlFileIfExists(File configurationFile, Consumer<Node> configure) {
        updateIdeaXmlFile(configurationFile, configure, false);
    }

    private static void updateIdeaXmlFile(File configurationFile, Consumer<Node> configure, boolean createIfAbsent) {
        Node rootNode;
        if (configurationFile.isFile()) {
            try {
                rootNode = new XmlParser().parse(configurationFile);
            } catch (IOException | SAXException | ParserConfigurationException e) {
                throw new RuntimeException("Couldn't parse existing configuration file: " + configurationFile, e);
            }
        } else {
            if (!createIfAbsent) {
                return;
            }
            rootNode = new Node(null, "project", ImmutableMap.of("version", "4"));
        }

        configure.accept(rootNode);

        try (BufferedWriter writer = Files.newWriter(configurationFile, StandardCharsets.UTF_8);
                PrintWriter printWriter = new PrintWriter(writer)) {
            XmlNodePrinter nodePrinter = new XmlNodePrinter(printWriter);
            nodePrinter.setPreserveWhitespace(true);
            nodePrinter.print(rootNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write back to configuration file: " + configurationFile, e);
        }
    }
}
