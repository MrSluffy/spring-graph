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

import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Reads Spring annotations from compiler elements, by annotation name. */
public final class Annotations {

    private static final String DEFAULT_SCOPE = "singleton";

    private Annotations() {}

    public static boolean has(Element element, String annotationName) {
        return find(element, annotationName) != null;
    }

    /** The annotation with the given qualified name directly on the element, or null. */
    public static AnnotationMirror find(Element element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (typeOf(mirror).getQualifiedName().contentEquals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    /**
     * Returns e.g. "@Profile" when the element carries {@code @Conditional} directly or an annotation that is itself
     * meta-annotated with {@code @Conditional} (covers {@code @Profile} and Boot's {@code @ConditionalOnX}); otherwise null.
     */
    public static String conditionalMarker(Element element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotationType = typeOf(mirror);
            if (annotationType.getQualifiedName().contentEquals(SpringTypes.CONDITIONAL)
                    || has(annotationType, SpringTypes.CONDITIONAL)) {
                return "@" + annotationType.getSimpleName();
            }
        }
        return null;
    }

    /** The {@code @Scope} name, "singleton" for {@code @Scope} without a value, or null without {@code @Scope}. */
    public static String scope(Element element) {
        AnnotationMirror scope = find(element, SpringTypes.SCOPE);
        if (scope == null) {
            return null;
        }
        String name = explicitString(scope, Set.of("value", "scopeName"));
        return name != null ? name : DEFAULT_SCOPE;
    }

    public static boolean isSingletonScope(String scope) {
        return scope == null || DEFAULT_SCOPE.equals(scope);
    }

    /**
     * Spring's default bean name for a scanned component: the stereotype's explicit {@code value}, otherwise the simple
     * class name with {@link java.beans.Introspector#decapitalize} rules (as AnnotationBeanNameGenerator). Nested classes
     * use "Outer.Inner", as Spring does.
     */
    public static String beanName(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (SpringTypes.STEREOTYPES.contains(
                    typeOf(mirror).getQualifiedName().toString())) {
                String explicit = explicitString(mirror, Set.of("value"));
                if (explicit != null) {
                    return explicit;
                }
            }
        }
        String simple = type.getNestingKind().isNested()
                ? type.getEnclosingElement().getSimpleName() + "." + type.getSimpleName()
                : type.getSimpleName().toString();
        if (simple.length() > 1 && Character.isUpperCase(simple.charAt(1)) && Character.isUpperCase(simple.charAt(0))) {
            return simple; // e.g. "URLService" stays "URLService"
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /** First explicitly set, non-empty String value among the given attributes, or null. */
    private static String explicitString(AnnotationMirror mirror, Set<String> attributes) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirror.getElementValues().entrySet()) {
            if (attributes.contains(entry.getKey().getSimpleName().toString())
                    && entry.getValue().getValue() instanceof String value
                    && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static TypeElement typeOf(AnnotationMirror mirror) {
        return (TypeElement) mirror.getAnnotationType().asElement();
    }
}
