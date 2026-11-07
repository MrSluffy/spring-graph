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
package io.springgraph.processor.internal.resolution;

import io.springgraph.processor.SpringGraphProcessor;
import io.springgraph.processor.internal.diagnostic.Code;
import io.springgraph.processor.internal.diagnostic.Diagnostics;
import io.springgraph.processor.internal.discovery.Annotations;
import io.springgraph.processor.internal.discovery.SpringTypes;
import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import io.springgraph.processor.internal.model.Resolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Classifies one constructor parameter. The rules are applied in a fixed order (see {@link #resolve}); the order is part of
 * the behavior, so add new rules deliberately and cover them in SpringGraphProcessorTest and SpringAgreementTest.
 *
 * <p>Guiding rule: when the processor cannot determine what Spring would do, the dependency is UNSUPPORTED (SG006/SG008),
 * never resolved and never reported as an error.
 */
public final class DependencyResolver {

    private final Types types;
    private final Elements elements;
    private final Set<String> boundaries;
    private final Diagnostics diagnostics;
    private final BoundaryUsage usage;

    public DependencyResolver(
            Types types, Elements elements, Set<String> boundaries, Diagnostics diagnostics, BoundaryUsage usage) {
        this.types = types;
        this.elements = elements;
        this.boundaries = boundaries;
        this.diagnostics = diagnostics;
        this.usage = usage;
    }

    /**
     * @param graph every discovered component (including excluded ones, which still count as candidates), sorted by name
     */
    public Dependency resolve(VariableElement parameter, Map<String, Component> graph) {
        TypeMirror declared = parameter.asType();
        TypeMirror requested = types.erasure(declared);
        String type = requested.toString();
        usage.requested(type);

        // 1. Parameter shapes that are not modelled. Checked before looking for candidates.
        if (Annotations.has(parameter, SpringTypes.VALUE)) {
            return Dependency.of(parameter, type, Resolution.VALUE);
        }
        if (Annotations.has(parameter, SpringTypes.QUALIFIER)) {
            return unsupported(
                    parameter,
                    type,
                    Code.SG008,
                    "@Qualifier on '" + parameter.getSimpleName()
                            + "' is not supported yet; the dependency is not validated.");
        }
        if (Annotations.has(parameter, SpringTypes.LAZY)) {
            return unsupported(
                    parameter,
                    type,
                    Code.SG006,
                    "@Lazy injection of " + type + " is not supported; the dependency is not validated.");
        }
        if (isGenericOrContainer(declared, type)) {
            return unsupported(
                    parameter,
                    type,
                    Code.SG006,
                    "Generic, collection, optional or provider injection of " + declared
                            + " is not supported; the dependency is not validated.");
        }

        // 2. Components that could satisfy the dependency.
        List<Component> candidates = candidates(requested, graph);

        // 3. Profile/conditional candidates: which one is active depends on the environment.
        if (candidates.stream().anyMatch(Component::isConditional)) {
            return conditionalCandidates(parameter, type, candidates);
        }
        if (candidates.size() == 1) {
            return new Dependency(
                    parameter,
                    type,
                    Resolution.STATIC,
                    List.of(candidates.get(0).name()));
        }
        if (candidates.size() > 1) {
            return multipleCandidates(parameter, type, candidates);
        }

        // 4. No candidate in this module.
        if (boundaries.contains(type)) {
            usage.applied(type);
            return Dependency.of(parameter, type, Resolution.BOUNDARY);
        }
        if (isSpringDataRepository(requested)) {
            return Dependency.of(parameter, type, Resolution.REPOSITORY);
        }
        return Dependency.of(parameter, type, Resolution.MISSING);
    }

    private static boolean isGenericOrContainer(TypeMirror declared, String erasedType) {
        return declared.getKind() == TypeKind.DECLARED
                && (!((DeclaredType) declared).getTypeArguments().isEmpty()
                        || SpringTypes.CONTAINER_TYPES.contains(erasedType));
    }

    private List<Component> candidates(TypeMirror requested, Map<String, Component> graph) {
        List<Component> result = new ArrayList<>();
        for (Component component : graph.values()) {
            if (types.isAssignable(types.erasure(component.type().asType()), requested)) {
                result.add(component);
            }
        }
        return result;
    }

    /** A declared boundary wins; otherwise the dependency is not validated. Never resolved, never ambiguous. */
    private Dependency conditionalCandidates(VariableElement parameter, String type, List<Component> candidates) {
        if (boundaries.contains(type)) {
            usage.applied(type);
            return Dependency.of(parameter, type, Resolution.BOUNDARY);
        }
        List<String> described = new ArrayList<>();
        for (Component candidate : candidates) {
            if (candidate.isConditional()) {
                described.add(candidate.name() + " (" + candidate.conditional() + ")");
            }
        }
        return unsupported(
                parameter,
                type,
                Code.SG006,
                "Candidates for " + type + " are selected by profile or condition, which is not supported: "
                        + String.join(", ", described) + ". The dependency is not validated.\n"
                        + "If Spring supplies it at runtime, declare it a runtime boundary (-A"
                        + SpringGraphProcessor.OPT_BOUNDARIES + "=" + type + ").");
    }

    private Dependency multipleCandidates(VariableElement parameter, String type, List<Component> candidates) {
        if (candidates.stream().anyMatch(c -> Annotations.has(c.type(), SpringTypes.PRIMARY))) {
            return unsupported(
                    parameter,
                    type,
                    Code.SG006,
                    "@Primary among candidates for " + type
                            + " is not supported yet; the dependency is not validated.");
        }

        // Spring falls back to matching the parameter name against bean names, but only when parameter names are in the
        // class file (javac -parameters; on by default in Spring Boot's Maven/Gradle plugins). The processor cannot see
        // compiler flags, so the outcome is unknown.
        String parameterName = parameter.getSimpleName().toString();
        List<Component> named = candidates.stream()
                .filter(c -> Annotations.beanName(c.type()).equals(parameterName))
                .toList();
        List<String> names = candidates.stream().map(Component::name).toList();
        if (named.size() == 1) {
            return unsupported(
                    parameter,
                    type,
                    Code.SG006,
                    "Several beans match " + type + " ("
                            + String.join(", ", names) + "), but parameter '" + parameterName
                            + "' matches the bean name of "
                            + named.get(0).name()
                            + ". Spring selects it only if the code is compiled with -parameters (Spring Boot's"
                            + " build plugins enable it); otherwise startup fails as ambiguous. The dependency is not validated."
                            + " Add @Qualifier(\"" + parameterName
                            + "\") to make the choice independent of compiler flags.");
        }
        return new Dependency(parameter, type, Resolution.AMBIGUOUS, names);
    }

    private boolean isSpringDataRepository(TypeMirror requested) {
        TypeElement repository = elements.getTypeElement(SpringTypes.SPRING_DATA_REPOSITORY);
        if (repository == null || requested.getKind() != TypeKind.DECLARED) {
            return false;
        }
        Element element = ((DeclaredType) requested).asElement();
        return element.getKind() == ElementKind.INTERFACE
                && !Annotations.has(element, SpringTypes.NO_REPOSITORY_BEAN)
                && types.isAssignable(requested, types.erasure(repository.asType()));
    }

    private Dependency unsupported(VariableElement parameter, String type, Code code, String message) {
        diagnostics.finding(code, parameter, message);
        return Dependency.of(parameter, type, Resolution.UNSUPPORTED);
    }
}
