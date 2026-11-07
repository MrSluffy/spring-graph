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
package io.springgraph.processor;

import java.util.Locale;
import java.util.Set;
import javax.lang.model.util.Elements;

/**
 * SG009 for declared runtime boundaries that do nothing. A silently ignored declaration would hide a missing binding, so
 * each one is reported: not on the classpath (usually a typo), never requested (stale), or requested but never applied.
 */
final class BoundaryChecker {

    /** Suggestions are offered for simple names within this edit distance. */
    private static final int MAX_SUGGESTION_DISTANCE = 3;

    private final Elements elements;
    private final Diagnostics diagnostics;

    BoundaryChecker(Elements elements, Diagnostics diagnostics) {
        this.elements = elements;
        this.diagnostics = diagnostics;
    }

    /** @param boundaries declared boundaries, in a deterministic (sorted) order */
    void check(Set<String> boundaries, BoundaryUsage usage) {
        for (String boundary : boundaries) {
            if (usage.wasApplied(boundary)) {
                continue;
            }
            String suggestion = closestRequestedType(boundary, boundaries, usage);
            String didYouMean = suggestion == null ? "" : " Did you mean '" + suggestion + "'?";
            if (elements.getTypeElement(boundary) == null) {
                diagnostics.finding(
                        Code.SG009,
                        null,
                        "Runtime boundary '" + boundary
                                + "' was not found on the compilation classpath, so it can never match a dependency."
                                + didYouMean);
            } else if (usage.wasRequested(boundary)) {
                diagnostics.warning(
                        Code.SG009,
                        "Runtime boundary '" + boundary
                                + "' is declared but does not apply to any dependency: every dependency requesting it is resolved"
                                + " statically or classified by another rule (@Value, @Qualifier, generics, conditional candidates"
                                + " without this declaration). Remove it, or it will mislead readers of the build configuration.");
            } else {
                diagnostics.warning(
                        Code.SG009,
                        "Runtime boundary '" + boundary
                                + "' is never requested by any analysed component. Remove it if it is stale."
                                + didYouMean);
            }
        }
    }

    /** The requested type whose simple name is closest to the boundary's, skipping boundaries already applied; or null. */
    private static String closestRequestedType(String boundary, Set<String> boundaries, BoundaryUsage usage) {
        String wanted = TextSimilarity.simpleName(boundary).toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = MAX_SUGGESTION_DISTANCE + 1;
        for (String candidate : usage.requestedTypes()) {
            if (boundaries.contains(candidate) && usage.wasApplied(candidate)) {
                continue;
            }
            int distance = TextSimilarity.editDistance(
                    wanted, TextSimilarity.simpleName(candidate).toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
