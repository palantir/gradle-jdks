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

java_home=$1
jdk_installation_directory=$2
if [ ! -d "$jdk_installation_directory" ]; then
  mkdir -p "$jdk_installation_directory"
  mv "$java_home"/* "$jdk_installation_directory"
  echo "Successfully installed JDK distribution, setting JAVA_HOME to $jdk_installation_directory"
else
  echo "Distribution $jdk_installation_directory already exists, setting JAVA_HOME to $jdk_installation_directory"
fi