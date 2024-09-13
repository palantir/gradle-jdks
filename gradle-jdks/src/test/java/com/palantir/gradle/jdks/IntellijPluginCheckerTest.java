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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class IntellijPluginCheckerTest {

    @Test
    public void minimum_version_intellij_plugin_exists()
            throws IOException, ParserConfigurationException, SAXException {
        URL url = new URL("https://plugins.jetbrains.com/plugins/list?pluginId=24776");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode())
                .isEqualTo(HttpURLConnection.HTTP_OK)
                .describedAs("Failed to query plugin version", conn.getResponseCode());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(conn.getInputStream());
        document.getDocumentElement().normalize();
        NodeList versionList = document.getElementsByTagName("version");
        assertThat(versionList.getLength()).isGreaterThan(0);
        String version = versionList.item(0).getTextContent();
        assertThat(version)
                .isGreaterThanOrEqualTo(PalantirGradleJdksIdeaPlugin.MIN_IDEA_PLUGIN_VERSION)
                .describedAs(
                        "If this test fails, then the minimum required Intellij plugin version is not yet published.");
    }
}
