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

package com.palantir.gradle.jdks

class ConfigureJdksIdeaPluginXml {

    static void configureExternalDependencies(Node rootNode) {
        def externalDependencies = matchOrCreateChild(rootNode, 'component', [name: 'ExternalDependencies'])
        matchOrCreateChild(externalDependencies, 'plugin', [id: 'palantir-gradle-jdks'])
    }

    private static Node matchOrCreateChild(Node base, String name, Map attributes = [:], Map defaults = [:]) {
        matchChild(base, name, attributes).orElseGet {
            base.appendNode(name, attributes + defaults)
        }
    }

    private static Optional<Node> matchChild(Node base, String name, Map attributes = [:]) {
        def child = base[name].find { it.attributes().entrySet().containsAll(attributes.entrySet()) }

        return Optional.ofNullable(child)
    }
}
