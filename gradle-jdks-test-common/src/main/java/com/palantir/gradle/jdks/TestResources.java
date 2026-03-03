/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestResources {

    public static final Jdk JDK_11 = new Jdk("azul-zulu", "11.54.25-11.0.14.1");
    public static final Jdk JDK_17 = new Jdk("amazon-corretto", "17.0.3.6.1");
    public static final Jdk JDK_21 = new Jdk("amazon-corretto", "21.0.2.13.1");
    public static final Jdk GRAALVM_3 = new Jdk("graalvm-ce", "23.0.1");
    public static final Jdks HARDCODED_JDKS = new Jdks(List.of(JDK_11, JDK_17, JDK_21));

    private static final Pattern LOCATION_PATTERN = Pattern.compile("Location:\\s+(.*)");
    private static final Pattern LANGUAGE_VERSION_PATTERN = Pattern.compile(" Language Version:\\s+(\\d+)");
    private static final Pattern DETECTED_BY = Pattern.compile("Detected by:\\s+(.*)");

    public static List<String> getDiscoveredLocations(String output) {
        return LOCATION_PATTERN
                .matcher(output)
                .results()
                .map(result -> result.group(1))
                .toList();
    }

    public static List<Integer> getLanguageVersions(String output) {
        return LANGUAGE_VERSION_PATTERN
                .matcher(output)
                .results()
                .map(result -> Integer.parseInt(result.group(1)))
                .toList();
    }

    public static List<String> getDetectedBy(String output) {
        return DETECTED_BY
                .matcher(output)
                .results()
                .map(result -> result.group(1))
                .toList();
    }

    public record Jdk(String distribution, String version) {
        public String toJdkExtension() {
            String majorVersion = version.substring(0, version.indexOf('.'));
            return String.format("""
                jdk(%s) {
                    distribution = '%s'
                    jdkVersion = '%s'
                }
                """, majorVersion, distribution, version);
        }

        public String toFileName() {
            return String.format("%s-%s", distribution, version);
        }
    }

    public record Jdks(List<Jdk> jdks) {
        public String toJdksExtension() {
            return jdks.stream().map(TestResources.Jdk::toJdkExtension).collect(Collectors.joining("\n"));
        }
    }

    private TestResources() {}
}
