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
import io.springgraph.processor.internal.discovery.Annotations.ClassQualifier;
import io.springgraph.processor.internal.discovery.SpringTypes;
import io.springgraph.processor.internal.model.Component;
import io.springgraph.processor.internal.model.Dependency;
import io.springgraph.processor.internal.model.Resolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Classifies one constructor parameter. The rules follow Spring 6.2's single-bean resolution
 * ({@code DefaultListableBeanFactory.doResolveDependency}) in its order, and stop where the processor cannot know the
 * outcome:
 * <ol>
 *   <li>Not modelled: {@code @Value}, custom/JSR-330 qualifiers, {@code @Lazy}, generic and container types.</li>
 *   <li>Candidates: components assignable to the type, filtered by the parameter's {@code @Qualifier} (a candidate
 *       matches by its class-level {@code @Qualifier} value or by its bean name), excluding the requesting component
 *       itself unless nothing else is left.</li>
 *   <li>Conditional candidates: never resolved.</li>
 *   <li>One candidate: resolved. Several: a unique {@code @Primary} wins, several {@code @Primary} are ambiguous, then a
 *       unique non-{@code @Fallback} candidate wins.</li>
 *   <li>Still several: Spring next compares bean names with the parameter name (needs {@code -parameters}) or the
 *       qualifier, then {@code @Priority}. Those outcomes are reported as not validated; otherwise ambiguous.</li>
 * </ol>
 * The order is part of the behavior. Cover every rule in SpringGraphProcessorTest and SpringAgreementTest.
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
        String customQualifier = Annotations.customQualifier(parameter);
        if (customQualifier != null) {
            return unsupported(
                    parameter,
                    type,
                    null,
                    Code.SG008,
                    customQualifier + " on '" + parameter.getSimpleName()
                            + "' is not supported; only Spring's @Qualifier with a value is. The dependency is not"
                            + " validated.");
        }
        String qualifier = Annotations.qualifierValue(parameter);
        if (qualifier != null && qualifier.isEmpty()) {
            return unsupported(
                    parameter,
                    type,
                    null,
                    Code.SG008,
                    "@Qualifier without a value on '" + parameter.getSimpleName()
                            + "' is not supported; the dependency is not validated.");
        }
        if (Annotations.has(parameter, SpringTypes.LAZY)) {
            return unsupported(
                    parameter,
                    type,
                    qualifier,
                    Code.SG006,
                    "@Lazy injection of " + type + " is not supported; the dependency is not validated.");
        }
        if (isGenericOrContainer(declared, type)) {
            return unsupported(
                    parameter,
                    type,
                    qualifier,
                    Code.SG006,
                    "Generic, collection, optional or provider injection of " + declared
                            + " is not supported; the dependency is not validated.");
        }

        // 2. Components that could satisfy the dependency.
        List<Component> byType = candidates(requested, graph);
        List<Component> matching = byType;
        if (qualifier != null) {
            Component unknown =
                    byType.stream().filter(c -> !isEvaluable(c)).findFirst().orElse(null);
            if (unknown != null) {
                return unsupported(
                        parameter,
                        type,
                        qualifier,
                        Code.SG008,
                        "@Qualifier(\"" + qualifier + "\") cannot be evaluated against " + unknown.name()
                                + ", which uses a custom qualifier or @Named. The dependency is not validated.");
            }
            matching =
                    byType.stream().filter(c -> matchesQualifier(c, qualifier)).toList();
        }
        List<Component> candidates = excludingSelf(matching, owner(parameter));

        // 3. Profile/conditional candidates: which one is active depends on the environment.
        if (candidates.stream().anyMatch(Component::isConditional)) {
            return conditionalCandidates(parameter, type, qualifier, candidates);
        }
        String reason = qualifier != null ? "@Qualifier" : null;
        if (candidates.size() == 1) {
            return Dependency.resolved(parameter, type, candidates.get(0).name(), qualifier, reason);
        }
        if (candidates.size() > 1) {
            return multipleCandidates(parameter, type, qualifier, candidates);
        }

        // 4. No candidate in this module.
        if (boundaries.contains(type)) {
            usage.applied(type);
            return Dependency.of(parameter, type, Resolution.BOUNDARY, qualifier);
        }
        if (isSpringDataRepository(requested)) {
            return Dependency.of(parameter, type, Resolution.REPOSITORY, qualifier);
        }
        return Dependency.of(parameter, type, Resolution.MISSING, qualifier);
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

    /** Qualifier matching needs a known bean name and a class qualifier the processor can read. */
    private static boolean isEvaluable(Component candidate) {
        return Annotations.beanName(candidate.type()) != null
                && !(Annotations.classQualifier(candidate.type()) instanceof ClassQualifier.Unknown);
    }

    /** Spring's QualifierAnnotationAutowireCandidateResolver: the class-level @Qualifier value, else the bean name. */
    private static boolean matchesQualifier(Component candidate, String qualifier) {
        if (Annotations.classQualifier(candidate.type()) instanceof ClassQualifier.Value value
                && value.value().equals(qualifier)) {
            return true;
        }
        return qualifier.equals(Annotations.beanName(candidate.type()));
    }

    /** Spring never injects a bean into itself while another candidate exists. */
    private static List<Component> excludingSelf(List<Component> candidates, String owner) {
        List<Component> others =
                candidates.stream().filter(c -> !c.name().equals(owner)).toList();
        return others.isEmpty() ? candidates : others;
    }

    private static String owner(VariableElement parameter) {
        Element constructor = parameter.getEnclosingElement();
        return ((TypeElement) constructor.getEnclosingElement())
                .getQualifiedName()
                .toString();
    }

    /** A declared boundary wins; otherwise the dependency is not validated. Never resolved, never ambiguous. */
    private Dependency conditionalCandidates(
            VariableElement parameter, String type, String qualifier, List<Component> candidates) {
        if (boundaries.contains(type)) {
            usage.applied(type);
            return Dependency.of(parameter, type, Resolution.BOUNDARY, qualifier);
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
                qualifier,
                Code.SG006,
                "Candidates for " + type + " are selected by profile or condition, which is not supported: "
                        + String.join(", ", described) + ". The dependency is not validated.\n"
                        + "If Spring supplies it at runtime, declare it a runtime boundary (-A"
                        + SpringGraphProcessor.OPT_BOUNDARIES + "=" + type + ").");
    }

    private Dependency multipleCandidates(
            VariableElement parameter, String type, String qualifier, List<Component> candidates) {
        List<String> names = candidates.stream().map(Component::name).toList();

        // Spring's determinePrimaryCandidate: a unique @Primary wins; several @Primary fail.
        List<Component> primaries = candidates.stream()
                .filter(c -> Annotations.isPresentOrMeta(c.type(), SpringTypes.PRIMARY))
                .toList();
        if (primaries.size() == 1) {
            return Dependency.resolved(parameter, type, primaries.get(0).name(), qualifier, "@Primary");
        }
        if (primaries.size() > 1) {
            return Dependency.ambiguous(
                    parameter, type, primaries.stream().map(Component::name).toList(), qualifier, "@Primary");
        }

        // Spring 6.2: without @Primary, a unique candidate that is not @Fallback wins.
        List<Component> nonFallback = candidates.stream()
                .filter(c -> !Annotations.isPresentOrMeta(c.type(), SpringTypes.FALLBACK))
                .toList();
        if (nonFallback.size() == 1) {
            return Dependency.resolved(parameter, type, nonFallback.get(0).name(), qualifier, "@Fallback");
        }

        // Next Spring compares bean names with the parameter name and the qualifier, then @Priority. The outcome
        // depends
        // on bean names, the -parameters compiler flag and priorities, so none of it is resolved.
        if (qualifier != null) {
            return unsupported(
                    parameter,
                    type,
                    qualifier,
                    Code.SG006,
                    "Several beans match " + type + " with @Qualifier(\"" + qualifier + "\") ("
                            + String.join(", ", names)
                            + "). Spring then chooses by bean name, which the processor does not model. The dependency"
                            + " is not validated.");
        }
        if (candidates.stream().anyMatch(c -> Annotations.beanName(c.type()) == null)) {
            return unsupported(
                    parameter,
                    type,
                    null,
                    Code.SG006,
                    "Several beans match " + type + " (" + String.join(", ", names)
                            + "), and at least one is named by @Named or @ManagedBean, which the processor does not"
                            + " model. The dependency is not validated.");
        }
        String parameterName = parameter.getSimpleName().toString();
        List<Component> named = candidates.stream()
                .filter(c -> Objects.equals(Annotations.beanName(c.type()), parameterName))
                .toList();
        if (named.size() == 1) {
            return unsupported(
                    parameter,
                    type,
                    null,
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
        if (candidates.stream()
                .anyMatch(c -> SpringTypes.PRIORITY.stream().anyMatch(p -> Annotations.has(c.type(), p)))) {
            return unsupported(
                    parameter,
                    type,
                    null,
                    Code.SG006,
                    "@Priority among candidates for " + type
                            + " is not supported yet; the dependency is not validated.");
        }
        return Dependency.ambiguous(parameter, type, names, null, null);
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

    private Dependency unsupported(
            VariableElement parameter, String type, String qualifier, Code code, String message) {
        diagnostics.finding(code, parameter, message);
        return Dependency.of(parameter, type, Resolution.UNSUPPORTED, qualifier);
    }
}
