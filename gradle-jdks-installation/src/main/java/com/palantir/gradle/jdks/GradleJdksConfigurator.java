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

import com.palantir.gradle.jdks.setup.common.Arch;
import com.palantir.gradle.jdks.setup.common.Os;
import java.util.Map;

public final class GradleJdksConfigurator {

    private static final JdkDistributions jdkDistributions = new JdkDistributions();

    public static GradleJdkUrls getGradleJdkUrls(
            String baseUrl,
            JdkDistributionName jdkDistributionName,
            Os os,
            Arch arch,
            String jdkVersion,
            Map<String, String> caCerts) {
        JdkSpec jdkSpec = JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(JdkRelease.builder()
                        .arch(arch)
                        .os(os)
                        .version(jdkVersion)
                        .build())
                .caCerts(CaCerts.from(caCerts))
                .build();
        String downloadUrl = resolveDownloadUrl(baseUrl, jdkSpec);
        String localPath = resolveLocalPath(jdkSpec);
        return new GradleJdkUrls(downloadUrl, localPath);
    }

    private static String resolveDownloadUrl(String baseUrl, JdkSpec jdkSpec) {
        JdkPath jdkPath = jdkDistributions.get(jdkSpec.distributionName()).path(jdkSpec.release());
        return String.format("%s/%s.%s", baseUrl, jdkPath.filename(), jdkPath.extension());
    }

    private static String resolveLocalPath(JdkSpec jdkSpec) {
        return String.format(
                "%s-%s-%s", jdkSpec.distributionName(), jdkSpec.release().version(), jdkSpec.consistentShortHash());
    }

    private GradleJdksConfigurator() {}
}
