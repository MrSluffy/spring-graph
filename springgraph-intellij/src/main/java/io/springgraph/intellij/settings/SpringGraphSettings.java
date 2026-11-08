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
package io.springgraph.intellij.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Project settings for the plugin, stored in {@code .idea/springgraph.xml} (shareable through VCS). They configure only
 * the IDE's own analysis with the bundled processor; the project's Gradle or Maven build is never changed.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpringGraphSettings", storages = @Storage("springgraph.xml"))
public final class SpringGraphSettings implements PersistentStateComponent<SpringGraphSettings.Data> {

    /** Which reports the tool window and gutter show. */
    public enum Source {
        /** A module's build report when its build runs the processor, otherwise the IDE analysis. */
        AUTO("Build report when available, otherwise IDE analysis"),
        /** Only reports written by the project's own build. */
        BUILD("Build reports only (processor configured in Gradle or Maven)"),
        /** Only the IDE analysis with the bundled processor. */
        IDE("IDE analysis only (bundled processor)");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Source parse(String value) {
            for (Source source : values()) {
                if (source.name().equals(value)) {
                    return source;
                }
            }
            return AUTO;
        }
    }

    public static final List<String> MODES = List.of("advisory", "strict");

    /** The persisted form. Public fields, as the IntelliJ serializer requires. */
    public static final class Data {
        public String source = Source.AUTO.name();
        public String mode = "advisory";
        /** Qualified type names separated by commas or line breaks. */
        public String runtimeBoundaries = "";

        public boolean analyzeAfterBuild = false;

        public Data copy() {
            Data copy = new Data();
            copy.source = source;
            copy.mode = mode;
            copy.runtimeBoundaries = runtimeBoundaries;
            copy.analyzeAfterBuild = analyzeAfterBuild;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Data d
                    && Objects.equals(source, d.source)
                    && Objects.equals(mode, d.mode)
                    && Objects.equals(runtimeBoundaries, d.runtimeBoundaries)
                    && analyzeAfterBuild == d.analyzeAfterBuild;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, mode, runtimeBoundaries, analyzeAfterBuild);
        }
    }

    private Data data = new Data();

    public static SpringGraphSettings getInstance(Project project) {
        return project.getService(SpringGraphSettings.class);
    }

    @Override
    public @NotNull Data getState() {
        return data;
    }

    @Override
    public void loadState(@NotNull Data state) {
        data = state.copy();
    }

    public Source source() {
        return Source.parse(data.source);
    }

    /** {@code advisory} or {@code strict}; anything else falls back to advisory. */
    public String mode() {
        return MODES.contains(data.mode) ? data.mode : "advisory";
    }

    /** The {@code -Aspringgraph.runtimeBoundaries} value. */
    public String runtimeBoundaries() {
        return normalizeBoundaries(data.runtimeBoundaries);
    }

    public boolean analyzeAfterBuild() {
        return data.analyzeAfterBuild;
    }

    /**
     * Splits on commas and any whitespace, drops empty and duplicate entries, and joins with commas. Entries are kept
     * otherwise as typed: the processor reports typos and unknown types as SG009.
     */
    public static String normalizeBoundaries(String raw) {
        if (raw == null) {
            return "";
        }
        Set<String> entries = new LinkedHashSet<>();
        for (String entry : raw.split("[,\\s]+")) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return String.join(",", new ArrayList<>(entries));
    }
}
