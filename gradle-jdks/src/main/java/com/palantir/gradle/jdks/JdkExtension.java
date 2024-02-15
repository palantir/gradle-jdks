/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.jdks.json.JdkInfoJson;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class JdkExtension {
    // Not called `version` to avoid being interfered with by `Project#setVersion`!
    public abstract Property<String> getJdkVersion();

    public abstract Property<JdkDistributionName> getDistributionName();

    private final Map<Os, JdkOsExtension> jdkOsExtensions = new HashMap<>();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public JdkExtension() {
        for (Os os : Os.values()) {
            JdkOsExtension jdkOsExtension = getObjectFactory().newInstance(JdkOsExtension.class);
            jdkOsExtension.getJdkVersion().set(getJdkVersion());
            jdkOsExtensions.put(os, jdkOsExtension);
        }
    }

    final JdkOsExtension jdkFor(Os os) {
        return jdkOsExtensions.get(os);
    }

    public final void setDistribution(JdkDistributionName jdkDistributionName) {
        getDistributionName().set(jdkDistributionName);
    }

    public final void setDistribution(String distributionName) {
        setDistribution(JdkDistributionName.fromStringThrowing(distributionName));
    }

    public final void os(Os os, Action<JdkOsExtension> action) {
        action.execute(jdkOsExtensions.get(os));
    }

    public final void os(String os, Action<JdkOsExtension> action) {
        os(Os.fromStringThrowing(os), action);
    }

    public final void fromJson(JdkInfoJson jdkInfoJson) {
        getDistributionName().set(jdkInfoJson.distribution());
        jdkInfoJson.os().forEach((os, osInfo) -> {
            os(os, osExtension -> {
                osExtension.fromJson(osInfo);
            });
        });
    }
}
