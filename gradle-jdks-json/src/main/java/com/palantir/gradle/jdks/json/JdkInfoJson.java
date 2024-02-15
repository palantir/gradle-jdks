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

package com.palantir.gradle.jdks.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.jdks.JdkDistributionName;
import com.palantir.gradle.jdks.Os;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableJdkInfoJson.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class JdkInfoJson {
    public abstract JdkDistributionName distribution();

    public abstract Optional<String> version();

    public abstract Map<Os, JdkOsInfoJson> os();

    public static final class Builder extends ImmutableJdkInfoJson.Builder {}

    public static Builder builder() {
        return new Builder();
    }
}
