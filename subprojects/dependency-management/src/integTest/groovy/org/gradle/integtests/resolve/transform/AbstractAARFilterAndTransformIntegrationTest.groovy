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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.TextUtil

/**
 * Overview of Configurations and 'formats' used in this scenario:
 *
 * Formats:
 * - aar                aar file
 * - jar                jar file
 * - classes            classes folder
 * - android-manifest   AndroidManifest.xml
 * - classpath          everything that can be in a JVM classpath (jar files, class folders, files treated as resources)
 *
 * Configurations:
 * - runtime                        behaves as runtime in Java plugin (e.g. packages classes in jars locally)
 * - compileClassesAndResources     provides all artifacts in its raw format (e.g. class folders, not jars)
 *
 * - processClasspath               filters and transforms to 'classpath' format (e.g. keeps jars, but extracts 'classes.jar' from external AAR)
 * - processClasses                 filters and transforms to 'classes' format (e.g. extracts jars to class folders)
 * - processManifests               filters for 'android-manifest' format (no transformation for local libraries, extraction from aar)
 */
abstract public class AbstractAARFilterAndTransformIntegrationTest extends AbstractDependencyResolutionTest {

    enum Feature {
        FILTER_LOCAL,       // Filter functionality - local artifacts (performance/issues/205)
        FILTER_EXTERNAL,    // Filter functionality - external artifacts (performance/issues/206)
        TRANSFORM           // Transform artifacts on-demand during filtering (performance/issues/206)
    }

    /**
     * Override to reduce the test scenario to only use a limited set of filter/transform features
     */
    def enabledFeatures() {
        [Feature.FILTER_LOCAL, Feature.FILTER_EXTERNAL, Feature.TRANSFORM]
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'fake-android-build'
            include 'java-lib'
            include 'android-lib'
            include 'android-app'
        """.stripIndent()

        buildFile << """
            enum FileFormat {
                JAR,
                AAR,
                CLASS_FOLDER,
                MANIFEST
            }

            enum ProcessingAspect {
                JVM_CLASSPATH,
                DEX,
                MANIFEST_MERGE
            }

            ${javaLibWithClassFolderArtifact('java-lib')}
            ${mockedAndroidLib('android-lib')}
            ${mockedAndroidApp('android-app')}

            ${aarTransform()}
            ${jarTransform()}
            ${classFolderTransform()}
        """.stripIndent()
    }

    def javaLibWithClassFolderArtifact(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Publish an external version as JAR
        mavenRepo.module("org.gradle", "ext-$name").publish()

        """
        project(':$name') {
            configurations.create('default')
            configurations {
                compileClassesAndResources
                runtime
            }
            configurations.default.extendsFrom = [configurations.compileClassesAndResources]


            task classes(type: Copy) {
                from file('classes/main')
                into file('build/classes/main')
            }

            task jar(type: Zip) {
                dependsOn classes
                from classes.destinationDir
                destinationDir = file('build/libs')
                baseName = '$name'
                extension = 'jar'
            }

            artifacts {
                compileClassesAndResources(classes.destinationDir) {
                    ${defineLocalArtifactAttribute('FileFormat', 'CLASS_FOLDER')}
                    builtBy classes
                }

                runtime jar
            }
        }
        """
    }

    def mockedAndroidLib(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Manifest and zipped code
        def aarImage = file('android-lib/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file('android-lib/classes').zipTo(aarImage.file('classes.jar'))

        // Publish an external version as AAR
        def module = mavenRepo.module("org.gradle", "ext-$name").hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                compileClassesAndResources
                runtime //compiles JAR as in Java plugin
                compileAAR
            }
            configurations.default.extendsFrom = [configurations.compileClassesAndResources]

            task classes(type: Copy) {
                from file('classes/main')
                into file('build/classes/main')
            }

            task jar(type: Zip) {
                dependsOn classes
                from classes.destinationDir
                destinationDir = file('aar-image')
                baseName = 'classes'
                extension = 'jar'
            }

            task aar(type: Zip) {
                dependsOn jar
                from file('aar-image')
                destinationDir = file('build')
                extension = 'aar'
            }

            artifacts {
                compileClassesAndResources(classes.destinationDir) {
                    ${defineLocalArtifactAttribute('FileFormat', 'CLASS_FOLDER')}
                    builtBy classes
                }
                compileClassesAndResources(file('aar-image/AndroidManifest.xml')) {
                    ${defineLocalArtifactAttribute('ProcessingAspect', 'MANIFEST_MERGE')}
                }

                runtime jar

                compileAAR aar
            }
        }
        """
    }

    def mockedAndroidApp(String name) {
        file('android-app').mkdirs()

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                compileClassesAndResources
                runtime

                // configurations with filtering/transformation over 'compile'
                processClasspath {
                    extendsFrom(compileClassesAndResources)
                    ${defineArtifactFilter('ProcessingAspect', 'JVM_CLASSPATH')} // 'classes' or 'jar'
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                        ${registerTransform('JarTransform')}
                        ${registerTransform('ClassesFolderClasspathTransform')}
                    }
                }
                processClasses {
                    extendsFrom(compileClassesAndResources)
                    ${defineArtifactFilter('ProcessingAspect', 'DEX')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                        ${registerTransform('JarTransform')}
                        ${registerTransform('ClassesFolderClasspathTransform')}
                    }
                }
                processJar {
                    extendsFrom(compileClassesAndResources)
                    ${defineArtifactFilter('FileFormat', 'JAR')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                        ${registerTransform('JarTransform')}
                        ${registerTransform('ClassesFolderClasspathTransform')}
                    }
                }
                processManifests {
                    extendsFrom(compileClassesAndResources)
                    ${defineArtifactFilter('ProcessingAspect', 'MANIFEST_MERGE')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                    }
                }

                processClassFolders {
                    extendsFrom(compileClassesAndResources)
                    attribute(Attribute.of(ArtifactName), new ArtifactName('main'))
                }
                processJarFiles {
                    extendsFrom(compileClassesAndResources)

                    attribute(Attribute.of(ArtifactExtension), new ArtifactExtension('jar'))
                }
            }

            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            task printArtifacts {
                dependsOn configurations[configuration]
                doLast {
                    configurations[configuration].incoming.artifacts.each { println it.file.absolutePath - rootDir }
                }
            }
        }
        """
    }

    def aarTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        class AarExtractor extends ArtifactTransform {
            private Project files

            private File explodedAar
            private File explodedJar

            void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                from.attribute(Attribute.of(ArtifactExtension.class), new ArtifactExtension("aar"))

                targetRegistry.newTarget().attribute(Attribute.of(FileFormat.class), FileFormat.JAR)
                targetRegistry.newTarget().attribute(Attribute.of(FileFormat.class), FileFormat.CLASS_FOLDER)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect.class), ProcessingAspect.JVM_CLASSPATH)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect.class), ProcessingAspect.DEX)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect.class), ProcessingAspect.MANIFEST_MERGE)
            }

            File transform(File input, AttributeContainer target) {
                assert input.name.endsWith('.aar')

                explodedAar = new File(outputDirectory, input.name + '/explodedAar')
                explodedJar = new File(outputDirectory, input.name + '/explodedClassesJar')

                if (!explodedAar.exists()) {
                    files.copy {
                        from files.zipTree(input)
                        into explodedAar
                    }
                }
                if (!explodedJar.exists()) {
                    files.copy {
                        from files.zipTree(new File(explodedAar, 'classes.jar'))
                        into explodedJar
                    }
                }

                if (target.getAttribute(Attribute.of(FileFormat.class)) == FileFormat.JAR) {
                    return new File(explodedAar, "classes.jar");
                }
                if (target.getAttribute(Attribute.of(ProcessingAspect.class)) == ProcessingAspect.JVM_CLASSPATH) {
                    return new File(explodedAar, "classes.jar");
                }

                if (target.getAttribute(Attribute.of(FileFormat.class)) == FileFormat.CLASS_FOLDER) {
                    return explodedJar;
                }
                if (target.getAttribute(Attribute.of(ProcessingAspect.class)) == ProcessingAspect.DEX) {
                    return explodedJar;
                }
                if (target.getAttribute(Attribute.of(ProcessingAspect.class)) == ProcessingAspect.MANIFEST_MERGE) {
                    return new File(explodedAar, "AndroidManifest.xml")
                }
                return null;
            }
        }
        """
    }

    def jarTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        class JarTransform extends ArtifactTransform {
            private Project files

            private File jar
            private File classesFolder

            void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                from.attribute(Attribute.of(ArtifactExtension), new ArtifactExtension("jar"))

                targetRegistry.newTarget().attribute(Attribute.of(FileFormat), FileFormat.CLASS_FOLDER)
                targetRegistry.newTarget().attribute(Attribute.of(FileFormat), FileFormat.JAR)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect), ProcessingAspect.DEX)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect), ProcessingAspect.JVM_CLASSPATH)
            }

            File transform(File input, AttributeContainer target) {
                jar = input

                classesFolder = new File(outputDirectory, input.name + "/classes")
                if (!classesFolder.exists()) {
                    files.copy {
                        from files.zipTree(input)
                        into classesFolder
                    }
                }

                if (target.getAttribute(Attribute.of(FileFormat.class)) == FileFormat.CLASS_FOLDER) {
                    return classesFolder;
                }
                if (target.getAttribute(Attribute.of(ProcessingAspect.class)) == ProcessingAspect.DEX) {
                    return classesFolder;
                }
                if (target.getAttribute(Attribute.of(FileFormat.class)) == FileFormat.JAR) {
                    return jar;
                }
                if (target.getAttribute(Attribute.of(ProcessingAspect.class)) == ProcessingAspect.JVM_CLASSPATH) {
                    return jar;
                }
                return null;
            }
        }
        """
    }

    def classFolderTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        class ClassesFolderClasspathTransform extends ArtifactTransform {
            private Project files

            void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                from.attribute(Attribute.of(FileFormat), FileFormat.CLASS_FOLDER)

                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect), ProcessingAspect.JVM_CLASSPATH)
                targetRegistry.newTarget().attribute(Attribute.of(ProcessingAspect), ProcessingAspect.DEX)
            }
        }
        """
    }

    def  registerTransform(String implementationName) {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        registerTransform($implementationName) {
            outputDirectory = project.file("transformed")
            files = project
        }
        """
    }

    def defineArtifactFilter(String type, String formatName) {
        if (!enabledFeatures().contains(Feature.FILTER_LOCAL) && !enabledFeatures().contains(Feature.FILTER_EXTERNAL)) {
            return ""
        }
        """
        attribute(Attribute.of($type), $type.$formatName)
        """
    }

    def defineLocalArtifactAttribute(String type, String formatName) {
        if (!enabledFeatures().contains(Feature.FILTER_LOCAL)) {
            return ""
        }
        """
        attributes.attribute(Attribute.of($type), $type.$formatName)
        """
    }

    def dependency(String notation) {
        dependency('compileClassesAndResources', notation)
    }

    def dependency(String configuration, String notation) {
        buildFile << """
            project(':android-app') {
                dependencies {
                    $configuration $notation
                }
            }
        """
    }

    def artifacts(String configuration) {
        executer.withArgument("-Pconfiguration=$configuration")

        assert succeeds('printArtifacts')

        def result = []
        TextUtil.normaliseFileSeparators(output).eachLine { line ->
            if (line.startsWith("/")) {
                result.add(line)
            }
        }
        result
    }
}
