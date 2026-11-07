/*
 * Copyright 2026 John Andrew Camu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springgraph.processor.internal.discovery;

import java.util.Set;

/**
 * Fully qualified names of the Spring types the processor recognizes. Matched by name, so the processor has no
 * dependency on Spring and works with any Spring version that uses these names.
 */
public final class SpringTypes {

    public static final String CONFIGURATION = "org.springframework.context.annotation.Configuration";
    public static final String SCOPE = "org.springframework.context.annotation.Scope";
    public static final String CONDITIONAL = "org.springframework.context.annotation.Conditional";
    public static final String LAZY = "org.springframework.context.annotation.Lazy";
    public static final String PRIMARY = "org.springframework.context.annotation.Primary";
    public static final String VALUE = "org.springframework.beans.factory.annotation.Value";
    public static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";
    public static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    public static final String NO_REPOSITORY_BEAN = "org.springframework.data.repository.NoRepositoryBean";

    /** Direct stereotypes only. Composed (meta-annotated) stereotypes are not supported. */
    public static final Set<String> STEREOTYPES = Set.of(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController");

    /** Injection shapes whose Spring semantics are not modelled (collections, optionals, providers). */
    public static final Set<String> CONTAINER_TYPES = Set.of(
            "java.util.Optional",
            "java.util.Collection",
            "java.util.List",
            "java.util.Set",
            "java.util.Map",
            "jakarta.inject.Provider",
            "javax.inject.Provider",
            "org.springframework.beans.factory.ObjectProvider",
            "org.springframework.beans.factory.ObjectFactory");

    private SpringTypes() {}
}
