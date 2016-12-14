/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.composite.internal.CompositeBuildIdeProjectResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeDependencyKey;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IdeaDependenciesProvider {


    private static final String MINUS = "minus";
    private static final String PLUS = "plus";
    private static final Function<Configuration, String> CONFIGURATION_NAME = new Function<Configuration, String>() {
        @Override
        public String apply(Configuration configuration) {
            return configuration.getName();
        }
    };

    private final IdeDependenciesExtractor dependenciesExtractor;
    private final ModuleDependencyBuilder moduleDependencyBuilder;
    private Transformer<FilePath, File> getPath;

    public IdeaDependenciesProvider(ServiceRegistry serviceRegistry) {
        this(new IdeDependenciesExtractor(), serviceRegistry);
    }

    IdeaDependenciesProvider(IdeDependenciesExtractor dependenciesExtractor, ServiceRegistry serviceRegistry) {
        this.dependenciesExtractor = dependenciesExtractor;
        moduleDependencyBuilder = new ModuleDependencyBuilder(CompositeBuildIdeProjectResolver.from(serviceRegistry));
    }

    public Set<Dependency> provide(final IdeaModule ideaModule) {
        getPath = new Transformer<FilePath, File>() {
            @Override
            @Nullable
            public FilePath transform(File file) {
                return file != null ? ideaModule.getPathFactory().path(file) : null;
            }
        };
        Set<Configuration> ideaConfigurations = ideaConfigurations(ideaModule);
        Set<Dependency> result = new LinkedHashSet<Dependency>();
        if (ideaModule.getSingleEntryLibraries() != null) {
            for (Map.Entry<String, Iterable<File>> singleEntryLibrary : ideaModule.getSingleEntryLibraries().entrySet()) {
                String scope = singleEntryLibrary.getKey();
                for (File file : singleEntryLibrary.getValue()) {
                    if (file != null && file.isDirectory()) {
                        result.add(new SingleEntryModuleLibrary(getPath.transform(file), scope));
                    }
                }
            }
        }
        result.addAll(provideFromScopeRuleMappings(ideaModule, ideaConfigurations));
        return result;
    }

    public Collection<UnresolvedIdeRepoFileDependency> getUnresolvedDependencies(final IdeaModule ideaModule) {
        Set<UnresolvedIdeRepoFileDependency> usedUnresolvedDependencies = Sets.newTreeSet(new Comparator<UnresolvedIdeRepoFileDependency>() {
            @Override
            public int compare(UnresolvedIdeRepoFileDependency left, UnresolvedIdeRepoFileDependency right) {
                return left.getDisplayName().compareTo(right.getDisplayName());
            }
        });
        Function<String, Configuration> toConfiguration = new Function<String, Configuration>() {
            @Override
            public Configuration apply(String input) {
                return ideaModule.getProject().getConfigurations().findByName(input);
            }
        };
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            if (shouldProcessScope(scope)) {
                Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
                if (plusMinusConfigurations == null) {
                    plusMinusConfigurations = Maps.newHashMap();
                }

                Collection<Configuration> minusConfigurations = toConfigurations(toConfiguration, minusConfigurations(scope, plusMinusConfigurations.get(MINUS)));
                Collection<Configuration> plusConfigurations = toConfigurations(toConfiguration, plusConfigurations(scope, plusMinusConfigurations.get(PLUS)));

                usedUnresolvedDependencies.addAll(dependenciesExtractor.unresolvedExternalDependencies(plusConfigurations, minusConfigurations));
            }
        }

        return usedUnresolvedDependencies;
    }

    private ImmutableSet<Configuration> toConfigurations(Function<String, Configuration> toConfiguration, Collection<String> iterable) {
        return ImmutableSet.copyOf(Iterables.filter(Iterables.transform(iterable, toConfiguration), Predicates.<Configuration>notNull()));
    }

    private Set<Dependency> provideFromScopeRuleMappings(IdeaModule ideaModule, Collection<Configuration> ideaConfigurations) {
        Multimap<IdeDependencyKey<?, Dependency>, String> dependencyToConfigurations = LinkedHashMultimap.create();
        Project project = ideaModule.getProject();
        for (Configuration configuration : ideaConfigurations) {
            // project dependencies
            Collection<IdeProjectDependency> ideProjectDependencies = dependenciesExtractor.extractProjectDependencies(
                project, Collections.singletonList(configuration), Collections.<Configuration>emptyList());
            for (IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forProjectDependency(
                    ideProjectDependency,
                    new IdeDependencyKey.DependencyBuilder<IdeProjectDependency, Dependency>() {
                        @Override
                        public Dependency buildDependency(IdeProjectDependency dependency, String scope) {
                            return moduleDependencyBuilder.create(dependency, scope);
                        }
                    });
                dependencyToConfigurations.put(key, configuration.getName());
            }
            // repository dependencies
            if (!ideaModule.isOffline()) {
                Collection<IdeExtendedRepoFileDependency> ideRepoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                    project.getDependencies(), Collections.singletonList(configuration), Collections.<Configuration>emptyList(),
                    ideaModule.isDownloadSources(), ideaModule.isDownloadJavadoc());
                for (IdeExtendedRepoFileDependency ideRepoFileDependency : ideRepoFileDependencies) {
                    IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forRepoFileDependency(
                        ideRepoFileDependency,
                        new IdeDependencyKey.DependencyBuilder<IdeExtendedRepoFileDependency, Dependency>() {
                            @Override
                            public Dependency buildDependency(IdeExtendedRepoFileDependency dependency, String scope) {
                                Set<FilePath> javadoc = CollectionUtils.collect(dependency.getJavadocFiles(), new LinkedHashSet<FilePath>(), getPath);
                                Set<FilePath> source = CollectionUtils.collect(dependency.getSourceFiles(), new LinkedHashSet<FilePath>(), getPath);
                                SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(
                                    getPath.transform(dependency.getFile()), javadoc, source, scope);
                                library.setModuleVersion(dependency.getId());
                                return library;
                            }
                        });
                    dependencyToConfigurations.put(key, configuration.getName());
                }
            }
            // file dependencies
            Collection<IdeLocalFileDependency> ideLocalFileDependencies = dependenciesExtractor.extractLocalFileDependencies(
                Collections.singletonList(configuration), Collections.<Configuration>emptyList());
            for (IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forLocalFileDependency(
                    fileDependency,
                    new IdeDependencyKey.DependencyBuilder<IdeLocalFileDependency, Dependency>() {
                        @Override
                        public Dependency buildDependency(IdeLocalFileDependency dependency, String scope) {
                            return new SingleEntryModuleLibrary(getPath.transform(dependency.getFile()), scope);
                        }
                    });
                dependencyToConfigurations.put(key, configuration.getName());
            }
        }

        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        Multimap<GeneratedIdeaScope, IdeDependencyKey<?, Dependency>> alreadyMapped = HashMultimap.create();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            if (shouldProcessScope(scope)) {
                Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
                if (plusMinusConfigurations == null) {
                    plusMinusConfigurations = Maps.newHashMap();
                }

                Collection<String> minusConfigurations = minusConfigurations(scope, plusMinusConfigurations.get(MINUS));
                Collection<String> plusConfigurations = plusConfigurations(scope, plusMinusConfigurations.get(PLUS));

                for (String plusConfiguration : plusConfigurations) {
                    Collection<IdeDependencyKey<?, Dependency>> matchingDependencies =
                        extractDependencies(dependencyToConfigurations, Collections.singletonList(plusConfiguration), minusConfigurations);
                    for (IdeDependencyKey<?, Dependency> dependencyKey : matchingDependencies) {
                        if (scope == GeneratedIdeaScope.COMPILE && alreadyMapped.get(GeneratedIdeaScope.PROVIDED).contains(dependencyKey)) {
                            continue;
                        }
                        if (scope == GeneratedIdeaScope.RUNTIME && alreadyMapped.get(GeneratedIdeaScope.COMPILE).contains(dependencyKey)) {
                            continue;
                        }
                        if (scope == GeneratedIdeaScope.TEST && alreadyMapped.get(GeneratedIdeaScope.RUNTIME).contains(dependencyKey)) {
                            continue;
                        }
                        alreadyMapped.put(scope, dependencyKey);
                        Iterables.addAll(dependencies, Iterables.transform(
                            scope.scopes,
                            scopeToDependency(dependencyKey)));
                    }
                }
            }
        }

        return dependencies;
    }

    private Collection<String> minusConfigurations(GeneratedIdeaScope scope, Collection<Configuration> minusConfigurations) {
        Set<String> result = Sets.newLinkedHashSet();
        switch (scope) {
            case COMPILE:
                break;
            case RUNTIME:
                result.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
                break;
            case TEST:
                result.add(JavaPlugin.COMPILE_CONFIGURATION_NAME);
                result.add(JavaPlugin.API_CONFIGURATION_NAME);
                result.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
                break;
            case PROVIDED:
                result.add(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
                result.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
                break;
        }
        if (minusConfigurations != null) {
            Iterables.addAll(result, Iterables.transform(minusConfigurations, CONFIGURATION_NAME));
        }
        return result;
    }

    private Collection<String> plusConfigurations(GeneratedIdeaScope scope, Collection<Configuration> plusConfigurations) {
        Set<String> result = Sets.newLinkedHashSet();
        switch (scope) {
            case COMPILE:
                result.add(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
                break;
            case RUNTIME:
                result.add(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                break;
            case TEST:
                result.add(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME);
                result.add(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                break;
            case PROVIDED:
                result.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
                break;
        }
        if (plusConfigurations != null) {
            Iterables.addAll(result, Iterables.transform(plusConfigurations, CONFIGURATION_NAME));
        }
        return result;
    }

    private boolean shouldProcessScope(GeneratedIdeaScope scope) {
        return !scope.composite;
    }

    private static Function<String, Dependency> scopeToDependency(final IdeDependencyKey<?, Dependency> dependencyKey) {
        return new Function<String, Dependency>() {
            @Override
            @Nullable
            public Dependency apply(String s) {
                return dependencyKey.buildDependency(s);
            }
        };
    }

    private Set<Configuration> ideaConfigurations(final IdeaModule ideaModule) {
        ConfigurationContainer configurationContainer = ideaModule.getProject().getConfigurations();
        Set<Configuration> configurations = Sets.newLinkedHashSet();
        for (Map<String, Collection<Configuration>> scopeMap : ideaModule.getScopes().values()) {
            for (Configuration cfg : Iterables.concat(scopeMap.values())) {
                configurations.add(cfg);
            }
        }
        for (GeneratedIdeaScope generatedIdeaScope : GeneratedIdeaScope.values()) {
            Iterable<String> confs =
                Iterables.concat(plusConfigurations(generatedIdeaScope, null), minusConfigurations(generatedIdeaScope, null));
            for (String conf : confs) {
                Configuration byName = configurationContainer.findByName(conf);
                if (byName != null) {
                    configurations.add(byName);
                }
            }
        }
        return configurations;
    }

    /**
     * Looks for dependencies contained in all configurations to remove them from multimap and return as result.
     */
    private List<IdeDependencyKey<?, Dependency>> extractDependencies(Multimap<IdeDependencyKey<?, Dependency>, String> dependenciesToConfigs,
                                                                      Collection<String> configurations, Collection<String> minusConfigurations) {
        List<IdeDependencyKey<?, Dependency>> deps = new ArrayList<IdeDependencyKey<?, Dependency>>();
        for (IdeDependencyKey<?, Dependency> dependencyKey : dependenciesToConfigs.keySet()) {
            if (dependenciesToConfigs.get(dependencyKey).containsAll(configurations)) {
                boolean isInMinus = false;
                for (String minusConfiguration : minusConfigurations) {
                    if (dependenciesToConfigs.get(dependencyKey).contains(minusConfiguration)) {
                        isInMinus = true;
                        break;
                    }
                }
                if (!isInMinus) {
                    deps.add(dependencyKey);
                }
            }
        }
        return deps;
    }
}
