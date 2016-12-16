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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleBackedArtifactBuilder
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion("current")
@TargetGradleVersion('>=3.3')
class IncompatibilityCrossVersionSpec extends ToolingApiSpecification {
    def buildPluginWith(String gradleVersion) {
        println "Building plugin with $gradleVersion"
        def pluginDir = file("plugin")
        def pluginJar = pluginDir.file("plugin.jar")
        def builder = new GradleBackedArtifactBuilder(new ForkingGradleExecuter(buildContext.distribution(gradleVersion), temporaryFolder), pluginDir)
        builder.sourceFile("com/example/MyTask.java") << """
            package com.example;
            
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            
            public class MyTask extends DefaultTask {
                public MyTask() {
                    getInputs();
                    getOutputs();
                }
            }
        """
        builder.buildJar(pluginJar)

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("${pluginJar.toURI()}")
                }
            }
            
            task myTask(type: com.example.MyTask)
        """
    }

    def "can use plugin built with Gradle 1.2 with"() {
        expect:
        buildPluginWith("1.2")
        assertWorks()
    }

    def "can use plugin built with Gradle 1.8 with"() {
        expect:
        buildPluginWith("1.8")
        assertWorks()
    }

    def "can use plugin built with Gradle 2.0 with"() {
        expect:
        buildPluginWith("2.0")
        assertWorks()
    }

    def "can use plugin built with Gradle 2.5 with"() {
        expect:
        buildPluginWith("2.5")
        assertWorks()
    }

    def "can use plugin built with Gradle 3.0 with"() {
        expect:
        buildPluginWith("3.0")
        assertWorks()
    }

    def "can use plugin built with Gradle 3.2.1 with"() {
        expect:
        buildPluginWith("3.2.1")
        assertWorks()
    }

    private void assertWorks() {
        runBuild()
    }

    private Object runBuild() {
        toolingApi.requireDaemons()
        withConnector { c ->
            // TestKit sets this to true when debugging
            c.embedded(true)
        }
        withConnection { c ->
            c.newBuild().forTasks("myTask").run()
        }
    }
}
