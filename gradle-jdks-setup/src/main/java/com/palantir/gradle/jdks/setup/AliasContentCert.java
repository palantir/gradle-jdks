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

/**
 * Helper class to represent a certificate based on the alias and content.
 */
public final class AliasContentCert {

    private final String content;

    private final String alias;

    public AliasContentCert(String alias, String content) {
        this.alias = alias;
        this.content = content;
    }

    public String getAlias() {
        return alias;
    }

    public String getContent() {
        return content;
    }
}
