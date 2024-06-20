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

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class NewStartParameterInternal extends StartParameterInternal {

    private static final Logger log = Logging.getLogger(NewStartParameterInternal.class);

    public NewStartParameterInternal() {
        super();
        log.lifecycle("creating new start parameter internal");
    }

    @Override
    public Map<String, String> getProjectProperties() {
        log.lifecycle("setting project properties");
        return super.getProjectProperties();
    }

    @Override
    public void setProjectProperties(Map<String, String> projectProperties) {
        Map<String, String> newProperties = Map.of(
                "org.gradle.java.installations.auto-detect",
                "false",
                "org.gradle.java.installations.auto-download",
                "false");
        log.lifecycle("setting project properties: {}", newProperties);
        super.setProjectProperties(
                Stream.concat(projectProperties.entrySet().stream(), newProperties.entrySet().stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
