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

import com.google.common.base.Strings;
import com.palantir.gradle.jdks.setup.AliasContentCert;
import com.palantir.gradle.jdks.setup.CaResources;
import com.palantir.gradle.jdks.setup.ILogger;
import com.palantir.gradle.jdks.setup.common.CurrentOs;
import com.palantir.gradle.jdks.setup.common.Os;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecOutput.StandardStreamContent;

public abstract class GradleAwareCaResources {
    private ILogger logger;

    @Inject
    public GradleAwareCaResources(ILogger logger) {
        this.logger = logger;
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    private Provider<Optional<byte[]>> systemCertificates(ILogger logger) {
        Os os = CurrentOs.get();
        switch (os) {
            case MACOS:
                return macosSystemCertificates();
            case LINUX_MUSL:
            case LINUX_GLIBC:
                return getProviderFactory().provider(() -> Optional.of(CaResources.linuxSystemCertificates()));
            case WINDOWS:
                logger.logError(String.format(
                        "Not attempting to read Palantir CA from system truststore "
                                + "as OS type '%s' does not yet support this",
                        os.uiName()));
                return getProviderFactory().provider(() -> Optional.empty());
        }
        throw new IllegalStateException("Unreachable code; all Os enum values should be handled");
    }

    public Provider<Optional<AliasContentCert>> readPalantirRootCaFromSystemTruststore() {
        return systemCertificates(logger).map(maybeCerts -> maybeCerts.flatMap(CaResources::selectPalantirCertificate));
    }

    private Provider<Optional<byte[]>> macosSystemCertificates() {
        Provider<String> certificates = Stream.of("/Library/Keychains/System.keychain")
                .map(Paths::get)
                .filter(Files::exists)
                .map(this::macosSystemCertificates)
                .map(ExecOutput::getStandardOutput)
                .map(StandardStreamContent::getAsText)
                .reduce(
                        getProviderFactory().provider(() -> ""),
                        (provider1, provider2) -> provider1.zip(provider2, (cert1, cert2) -> cert1 + "\n" + cert2));
        // Reducing an empty stream returns "", which we transform to Optional.empty()
        Provider<Optional<String>> maybeCertificates =
                certificates.map(certs -> Optional.ofNullable(Strings.emptyToNull(certs)));
        return maybeCertificates.map(certsMaybe -> certsMaybe.map(certs -> certs.getBytes(StandardCharsets.UTF_8)));
    }

    private ExecOutput macosSystemCertificates(Path keyChainPath) {
        return getProviderFactory()
                .exec(execSpec -> execSpec.commandLine(
                        "security",
                        "export",
                        "-t",
                        "certs",
                        "-f",
                        "pemseq",
                        "-k",
                        keyChainPath.toAbsolutePath().toString()));
    }
}
