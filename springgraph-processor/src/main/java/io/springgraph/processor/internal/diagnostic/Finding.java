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
package io.springgraph.processor.internal.diagnostic;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

/**
 * An emitted diagnostic, recorded for the machine-readable report.
 *
 * @param code      the diagnostic code
 * @param kind      the severity it was emitted with
 * @param component qualified name of the class the diagnostic is attached to, or null
 * @param parameter index of the constructor parameter it is attached to, or null
 * @param message   the message without the {@code "SGnnn: "} prefix
 */
public record Finding(Code code, Diagnostic.Kind kind, String component, Integer parameter, String message) {

    static Finding of(Code code, Diagnostic.Kind kind, Element element, String message) {
        if (element instanceof VariableElement variable
                && variable.getEnclosingElement() instanceof ExecutableElement constructor) {
            int index = constructor.getParameters().indexOf(variable);
            return new Finding(code, kind, typeName(constructor), index < 0 ? null : index, message);
        }
        return new Finding(code, kind, typeName(element), null, message);
    }

    /** The qualified name of {@code element} or of its nearest enclosing class; null when there is none. */
    private static String typeName(Element element) {
        for (Element e = element; e != null; e = e.getEnclosingElement()) {
            if (e instanceof TypeElement type) {
                return type.getQualifiedName().toString();
            }
        }
        return null;
    }
}
