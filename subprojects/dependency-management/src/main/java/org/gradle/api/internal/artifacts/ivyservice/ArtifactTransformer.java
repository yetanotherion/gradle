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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.io.Files;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.AttributesSchema;
import org.gradle.api.Buildable;
import org.gradle.api.HasAttributes;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.attributes.ArtifactClassifier;
import org.gradle.api.artifacts.attributes.ArtifactDefaultAttribute;
import org.gradle.api.artifacts.attributes.ArtifactExtension;
import org.gradle.api.artifacts.attributes.ArtifactName;
import org.gradle.api.artifacts.attributes.ArtifactType;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.transform.internal.ArtifactTransforms;
import org.gradle.api.internal.AttributeContainerInternal;
import org.gradle.api.internal.DefaultAttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.component.model.DefaultIvyArtifactName;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactTransformer {
    private final ConfigurationInternal configuration;
    private final AttributesSchema attributesSchema;
    private final Map<File, File> transformed = new HashMap<File, File>();

    public ArtifactTransformer(ConfigurationInternal configuration, AttributesSchema attributesSchema) {
        this.configuration = configuration;
        this.attributesSchema = attributesSchema;
    }

    private boolean matchArtifactsAttributes(HasAttributes artifact, AttributeContainer configuration) {
        ComponentAttributeMatcher matcher = new ComponentAttributeMatcher(attributesSchema, null,
            Collections.singleton(artifact), configuration);
        return matcher.getFullMatchs().contains(artifact) || matcher.getPartialMatchs().contains(artifact);
    }

    private Transformer<File, File> getTransform(HasAttributes from, HasAttributes to) {
        for (ArtifactTransforms.DependencyTransformRegistration transformReg : configuration.getResolutionStrategy().getTransforms()) {
            if (matchArtifactsAttributes(from,  transformReg.getFrom())
                && matchArtifactsAttributes(to, transformReg.getTo())) {
                return transformReg.getTransformer();
            }
        }
        return null;
    }

    /**
     * Returns a spec that selects artifacts with the given format, or which can be transformed into the given format.
     */
    public Spec<ResolvedArtifact> select() {
        if (configuration == null || configuration.getAttributes().isEmpty()) {
            return Specs.satisfyAll();
        }
        return new Spec<ResolvedArtifact>() {
            @Override
            public boolean isSatisfiedBy(ResolvedArtifact artifact) {
                return matchArtifactsAttributes(artifact, configuration.getAttributes())
                    || getTransform(artifact, configuration) != null;
            }
        };
    }

    /**
     * Returns a visitor that transforms files and artifacts to the given format and then forwards the results to the given visitor.
     */
    public ArtifactVisitor visitor(final ArtifactVisitor visitor) {
        if (configuration == null || configuration.getAttributes().isEmpty()) {
            return visitor;
        }
        return new ArtifactVisitor() {
            @Override
            public void visitArtifact(final ResolvedArtifact artifact) {
                if (matchArtifactsAttributes(artifact, configuration.getAttributes())) {
                    visitor.visitArtifact(artifact);
                    return;
                }
                final Transformer<File, File> transform = getTransform(artifact, configuration);
                if (transform == null) {
                    return;
                }
                TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

                AttributeContainer transformedAttributes = ((AttributeContainerInternal) configuration.getAttributes()).copy();
                String transformedName = getAttributeValue(transformedAttributes, ArtifactName.ATTRIBUTE, artifact.getName());
                String transformedType = getAttributeValue(transformedAttributes, ArtifactType.ATTRIBUTE, artifact.getType());
                String transformedExtension = getAttributeValue(transformedAttributes, ArtifactExtension.ATTRIBUTE, artifact.getExtension());
                String transformedClassifier = getAttributeValue(transformedAttributes, ArtifactClassifier.ATTRIBUTE, artifact.getClassifier());

                visitor.visitArtifact(new DefaultResolvedArtifact(artifact.getModuleVersion().getId(),
                    new DefaultIvyArtifactName(transformedName, transformedType, transformedExtension, transformedClassifier, transformedAttributes), artifact.getId(), buildDependencies, new Factory<File>() {
                    @Override
                    public File create() {
                        File file = artifact.getFile();
                        File transformedFile = transformed.get(file);
                        if (transformedFile == null) {
                            transformedFile = transform.transform(file);
                            transformed.put(file, transformedFile);
                        }
                        return transformedFile;
                    }
                }));
            }

            @Override
            public boolean includeFiles() {
                return visitor.includeFiles();
            }

            @Override
            public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
                List<File> result = new ArrayList<File>();
                for (File file : files) {
                    HasAttributes fileWithAttributes = new FileWithAttributes(file);
                    if (matchArtifactsAttributes(fileWithAttributes, configuration.getAttributes())) {
                        result.add(file);
                        continue;
                    }
                    File transformedFile = transformed.get(file);
                    if (transformedFile != null) {
                        result.add(transformedFile);
                        continue;
                    }
                    Transformer<File, File> transform = getTransform(fileWithAttributes, configuration);
                    if (transform == null) {
                        continue;
                    }
                    transformedFile = transform.transform(file);
                    transformed.put(file, transformedFile);
                    result.add(transformedFile);
                }
                if (!result.isEmpty()) {
                    visitor.visitFiles(componentIdentifier, result);
                }
            }

            private String getAttributeValue(AttributeContainer transformedAttributes, Attribute<? extends ArtifactDefaultAttribute> attribute,  String defaultValue) {
                ArtifactDefaultAttribute attributeValue = transformedAttributes.getAttribute(attribute);
                if (attributeValue != null) {
                    return attributeValue.getValue();
                } else {
                    return defaultValue;
                }
            }
        };
    }

    private static class FileWithAttributes implements HasAttributes {

        private File file;
        private AttributeContainer attributes;

        public FileWithAttributes(File file) {
            this.file = file;
            this.attributes = defaultFileAttributes(file);
        }

        private AttributeContainer defaultFileAttributes(File file) {
            DefaultAttributeContainer attributes = new DefaultAttributeContainer();
            attributes.attribute(ArtifactName.ATTRIBUTE, new ArtifactName(file.getName()));
            String fileExtension = Files.getFileExtension(file.getName());
            if (!"".equals(fileExtension)) {
                attributes.attribute(ArtifactType.ATTRIBUTE, new ArtifactType(fileExtension));
                attributes.attribute(ArtifactExtension.ATTRIBUTE, new ArtifactExtension(fileExtension));
            }
            return attributes;
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }
    }
}
