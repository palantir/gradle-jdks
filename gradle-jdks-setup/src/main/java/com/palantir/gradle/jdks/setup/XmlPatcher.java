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

package com.palantir.gradle.jdks.setup;

import java.io.IOException;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class XmlPatcher {

    private static final String GRADLE_LOCAL_JAVA_HOME = "#GRADLE_LOCAL_JAVA_HOME";

    public static void updateGradleJvmValue(Path xmlFilePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFilePath.toFile());
            document.getDocumentElement().normalize();

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();
            String xpathExpression = "//GradleProjectSettings/option[@name='gradleJvm']";

            Node gradleJvmOption = (Node) xpath.evaluate(xpathExpression, document, XPathConstants.NODE);
            if (gradleJvmOption != null) {
                Element element = (Element) gradleJvmOption;
                element.setAttribute("value", GRADLE_LOCAL_JAVA_HOME);
                System.out.println(
                        String.format("Updated the gradleJvm to GRADLE_LOCAL_JAVA_HOME in file `%s`.", xmlFilePath));
            } else {
                // Creating the new gradleJvm option
                Node gradleProjectSettings =
                        (Node) xpath.evaluate("//GradleProjectSettings", document, XPathConstants.NODE);
                Element newOption = document.createElement("option");
                newOption.setAttribute("name", "gradleJvm");
                newOption.setAttribute("value", GRADLE_LOCAL_JAVA_HOME);
                gradleProjectSettings.appendChild(newOption);
                System.out.println(
                        String.format("Added the gradleJvm configuration setting to file `%s`.", xmlFilePath));
            }
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(xmlFilePath.toFile());
            transformer.transform(source, result);
        } catch (ParserConfigurationException
                | SAXException
                | IOException
                | TransformerException
                | XPathExpressionException e) {
            throw new RuntimeException("Failed to update the gradleJvm option in the XML file.", e);
        }
    }
}
