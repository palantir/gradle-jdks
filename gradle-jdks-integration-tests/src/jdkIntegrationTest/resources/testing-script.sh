#!/bin/sh
#
# (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -e

source /gradle/gradle-jdk-resolver.sh
echo "Java home is: $JAVA_HOME"
echo "Java path is: $(type -p java)"
echo "Java version is: $(java --version | awk '{print $2}' | head -n 1)"
