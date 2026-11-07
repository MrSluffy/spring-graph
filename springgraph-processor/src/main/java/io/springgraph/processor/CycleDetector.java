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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds cycles in a directed graph of names. Pure graph code with no compiler types, so it can be tested on its own.
 *
 * <p>Depth-first search from every node in the map's iteration order. Each distinct cycle is returned once, in the order it
 * is first found, starting at the node where the search re-entered it. Two cycles are the same when one is a rotation of the
 * other.
 */
final class CycleDetector {

    private final Map<String, List<String>> edges;
    private final Set<String> finished = new HashSet<>();
    private final Set<String> seenCycles = new HashSet<>();
    private final List<List<String>> cycles = new ArrayList<>();

    private CycleDetector(Map<String, List<String>> edges) {
        this.edges = edges;
    }

    /** @param edges node to its successors; iterate in a deterministic order (e.g. a TreeMap) for deterministic results */
    static List<List<String>> findCycles(Map<String, List<String>> edges) {
        CycleDetector detector = new CycleDetector(edges);
        for (String start : edges.keySet()) {
            detector.visit(start, new ArrayDeque<>(), new HashSet<>());
        }
        return detector.cycles;
    }

    private void visit(String node, Deque<String> path, Set<String> onPath) {
        if (finished.contains(node)) {
            return;
        }
        path.addLast(node);
        onPath.add(node);
        for (String next : edges.getOrDefault(node, List.of())) {
            if (onPath.contains(next)) {
                List<String> pathList = new ArrayList<>(path);
                List<String> cycle = List.copyOf(pathList.subList(pathList.indexOf(next), pathList.size()));
                if (seenCycles.add(canonical(cycle))) {
                    cycles.add(cycle);
                }
            } else {
                visit(next, path, onPath);
            }
        }
        path.removeLast();
        onPath.remove(node);
        finished.add(node);
    }

    /** Rotation starting at the smallest name, so every rotation of a cycle has the same key. */
    private static String canonical(List<String> cycle) {
        int min = 0;
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(min)) < 0) {
                min = i;
            }
        }
        List<String> rotated = new ArrayList<>(cycle.size());
        for (int i = 0; i < cycle.size(); i++) {
            rotated.add(cycle.get((min + i) % cycle.size()));
        }
        return String.join(">", rotated);
    }
}
