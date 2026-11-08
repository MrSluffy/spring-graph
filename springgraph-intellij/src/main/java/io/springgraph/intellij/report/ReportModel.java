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
package io.springgraph.intellij.report;

import java.util.List;
import java.util.Objects;

/**
 * The processor's {@code META-INF/springgraph/graph.json}, schema version 1 (docs/reference/report-json.md). Filled by
 * Gson; unknown fields are ignored, so newer processors that only add fields stay readable.
 */
public final class ReportModel {

    public int schemaVersion;
    public String mode;
    public Coverage coverage;
    public List<Component> components;
    public List<Excluded> excluded;
    public List<Finding> findings;

    public boolean isStrict() {
        return "strict".equals(mode);
    }

    public List<Component> components() {
        return Objects.requireNonNullElse(components, List.of());
    }

    public List<Excluded> excluded() {
        return Objects.requireNonNullElse(excluded, List.of());
    }

    public List<Finding> findings() {
        return Objects.requireNonNullElse(findings, List.of());
    }

    public static final class Coverage {
        public int validated;
        public int total;
    }

    public static final class Component {
        public String name;
        /** E.g. "@Profile"; null when unconditional. */
        public String conditional;

        public List<Dependency> dependencies;

        public List<Dependency> dependencies() {
            return Objects.requireNonNullElse(dependencies, List.of());
        }
    }

    public static final class Dependency {
        public int index;
        public String parameter;
        /** Erased, fully qualified type. */
        public String type;

        public String qualifier;
        public String resolution;
        public List<String> targets;
        /** For STATIC: the rule that chose the target ("@Primary", ...); for AMBIGUOUS: "@Primary" or null. */
        public String reason;

        public Resolution resolution() {
            return Resolution.parse(resolution);
        }

        public List<String> targets() {
            return Objects.requireNonNullElse(targets, List.of());
        }
    }

    public static final class Excluded {
        public String name;
        public String reason;
        public boolean configuration;
    }

    public static final class Finding {
        public String code;
        /** "ERROR" or "WARNING". */
        public String severity;
        /** The class it is attached to, or null. */
        public String component;
        /** The constructor parameter index it is attached to, or null. */
        public Integer parameter;

        public String message;

        public boolean isError() {
            return "ERROR".equals(severity);
        }

        /** The first line of the message; the rest is the dependency path and fix hints. */
        public String summary() {
            String text = Objects.requireNonNullElse(message, "");
            int end = text.indexOf('\n');
            return end < 0 ? text : text.substring(0, end);
        }
    }

    /** How a dependency was classified. {@link #UNKNOWN} covers values added by a newer processor. */
    public enum Resolution {
        STATIC,
        BOUNDARY,
        REPOSITORY,
        VALUE,
        MISSING,
        AMBIGUOUS,
        UNSUPPORTED,
        UNKNOWN;

        static Resolution parse(String value) {
            for (Resolution r : values()) {
                if (r.name().equals(value)) {
                    return r;
                }
            }
            return UNKNOWN;
        }
    }
}
