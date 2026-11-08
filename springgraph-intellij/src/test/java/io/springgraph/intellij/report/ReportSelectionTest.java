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

import static org.junit.Assert.assertEquals;

import io.springgraph.intellij.settings.SpringGraphSettings;
import io.springgraph.intellij.settings.SpringGraphSettings.Source;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ReportSelectionTest {

    // Gradle-style: app.main and app.test share app/build; lib.main has no build report.
    private static final Path APP_BUILD = Path.of("app/build/classes/java/main/META-INF/springgraph/graph.json");
    private static final Path LIB_BUILD = Path.of("lib/build/classes/java/main/META-INF/springgraph/graph.json");
    private static final Path APP_IDE = Path.of("cache/app.main/graph.json");
    private static final Path LIB_IDE = Path.of("cache/lib.main/graph.json");

    private static final Map<String, Set<Path>> BUILD = new LinkedHashMap<>();
    private static final Map<String, Path> IDE = new LinkedHashMap<>();

    static {
        BUILD.put("app.main", Set.of(APP_BUILD));
        BUILD.put("app.test", Set.of(APP_BUILD));
        BUILD.put("lib.main", Set.of(LIB_BUILD));
        IDE.put("app.main", APP_IDE);
        IDE.put("app.test", Path.of("cache/app.test/graph.json"));
        IDE.put("lib.main", LIB_IDE);
    }

    private static final Set<Path> EXISTING = Set.of(APP_BUILD, APP_IDE, LIB_IDE);

    private static List<String> select(Source source) {
        return ReportSelection.select(source, BUILD, IDE, EXISTING::contains, Path::toString).stream()
                .map(c -> c.origin() + " " + c.label())
                .toList();
    }

    @Test
    public void autoPrefersTheBuildAndFillsGapsWithTheIdeAnalysis() {
        assertEquals(List.of("BUILD " + APP_BUILD, "IDE lib.main"), select(Source.AUTO));
    }

    @Test
    public void buildOnlyIgnoresIdeReports() {
        assertEquals(List.of("BUILD " + APP_BUILD), select(Source.BUILD));
    }

    @Test
    public void ideOnlyIgnoresBuildReports() {
        assertEquals(List.of("IDE app.main", "IDE lib.main"), select(Source.IDE));
    }

    @Test
    public void modulesWithBuildReportsAreSkippedByTheAutoAnalysis() {
        assertEquals(
                Set.of("app.main", "app.test"), ReportSelection.modulesWithBuildReports(BUILD, EXISTING::contains));
    }

    @Test
    public void boundariesAreNormalized() {
        assertEquals(
                "java.time.Clock,com.example.Gateway",
                SpringGraphSettings.normalizeBoundaries(" java.time.Clock,\n com.example.Gateway\tjava.time.Clock ,"));
        assertEquals("", SpringGraphSettings.normalizeBoundaries(null));
        assertEquals("", SpringGraphSettings.normalizeBoundaries(" \n, "));
    }
}
