/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.jdks.setup.AliasContentCert;
import com.palantir.gradle.jdks.setup.CertificateUtils;
import com.palantir.gradle.jdks.setup.ILogger;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public abstract class GradleAwareCaResources {
    private ILogger logger;
    private GradleAwareCertificateSource certificateSource;

    @Inject
    public GradleAwareCaResources(ILogger logger, GradleAwareCertificateSource certificateSource) {
        this.logger = logger;
        this.certificateSource = certificateSource;
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public Provider<Optional<AliasContentCert>> readPalantirRootCaFromSystemTruststore() {
        Optional<Provider<byte[]>> certProviderOpt = certificateSource.systemCertificates(logger);
        return certProviderOpt
                .map(provider -> provider.map(CertificateUtils::selectPalantirCertificate))
                .orElseGet(() -> getProviderFactory().provider(() -> Optional.empty()));
    }
}
