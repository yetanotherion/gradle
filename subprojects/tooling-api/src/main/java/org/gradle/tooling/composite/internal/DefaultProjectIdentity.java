/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.composite.BuildIdentity;
import org.gradle.tooling.composite.ProjectIdentity;

import java.io.File;

public class DefaultProjectIdentity implements ProjectIdentity {
    private final BuildIdentity build;
    private final File rootDir;
    private final String projectPath;

    public DefaultProjectIdentity(BuildIdentity build, File rootDir, String projectPath) {
        this.build = build;
        this.rootDir = rootDir;
        this.projectPath = projectPath;
    }

    @Override
    public BuildIdentity getBuild() {
        return build;
    }

    @Override
    public String toString() {
        return String.format("project={ %s:%s, (%s) }", rootDir.getName(), projectPath, build);
    }
}
