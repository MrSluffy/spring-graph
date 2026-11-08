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

import io.springgraph.processor.internal.config.Mode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Emits diagnostics as {@code "SGnnn: message"} with the severity rules from docs/reference/diagnostics.md, and records
 * them, in emission order, for the machine-readable report.
 */
public final class Diagnostics {

    private final Messager messager;
    private final Mode mode;
    private final List<Finding> findings = new ArrayList<>();

    public Diagnostics(Messager messager, Mode mode) {
        this.messager = messager;
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /** A finding: error in strict mode, warning in advisory mode. Attached to {@code element} when it is not null. */
    public void finding(Code code, Element element, String message) {
        print(mode.findingKind(), code, element, message);
    }

    /** Always a warning, regardless of mode. */
    public void warning(Code code, String message) {
        print(Diagnostic.Kind.WARNING, code, null, message);
    }

    /** An informational note without a code. */
    public void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    /** Every coded diagnostic emitted so far, in emission order. */
    public List<Finding> findings() {
        return Collections.unmodifiableList(findings);
    }

    private void print(Diagnostic.Kind kind, Code code, Element element, String message) {
        findings.add(Finding.of(code, kind, element, message));
        String text = code.name() + ": " + message;
        if (element == null) {
            messager.printMessage(kind, text);
        } else {
            messager.printMessage(kind, text, element);
        }
    }
}
