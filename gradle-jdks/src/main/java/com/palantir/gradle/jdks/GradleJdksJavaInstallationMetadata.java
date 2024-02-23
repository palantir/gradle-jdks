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

import java.lang.reflect.Proxy;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

final class GradleJdksJavaInstallationMetadata {
    public static JavaInstallationMetadata create(
            JavaLanguageVersion javaLanguageVersion,
            String javaRuntimeVersion,
            String jvmVersion,
            String vendor,
            Provider<Directory> installationPath) {
        return (JavaInstallationMetadata) Proxy.newProxyInstance(
                GradleJdksJavaInstallationMetadata.class.getClassLoader(),
                new Class[] {JavaInstallationMetadata.class},
                (_proxy, method, args) -> {
                    if (args != null && args.length != 0) {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method + " with " + args.length + " args");
                    }

                    switch (method.getName()) {
                        case "getLanguageVersion":
                            return javaLanguageVersion;
                        case "getJavaRuntimeVersion":
                            return javaRuntimeVersion;
                        case "getJvmVersion":
                            return jvmVersion;
                        case "getVendor":
                            return vendor;
                        case "getInstallationPath":
                            return installationPath.get();
                        case "isCurrentJvm":
                            return false;
                    }

                    throw new UnsupportedOperationException("Unsupported method: " + method);
                });
    }

    private GradleJdksJavaInstallationMetadata() {}
}
