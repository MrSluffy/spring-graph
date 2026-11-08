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
package io.springgraph.intellij;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds where builds put {@code graph.json}. Requires a read action; does no file I/O itself.
 *
 * <p>Checked for every module: its IntelliJ compiler output (the IntelliJ build system), and the Gradle and Maven main
 * output directories of its content roots and their parents. Gradle imports one module per source set, whose content root
 * is {@code src/main}, so the build directory is up to two levels above it.
 */
final class ReportLocator {

    static final String REPORT = "META-INF/springgraph/graph.json";

    private static final String[] BUILD_OUTPUTS = {"build/classes/java/main", "target/classes"};
    private static final int PARENT_LEVELS = 2;

    private ReportLocator() {}

    static Set<Path> candidates(Project project) {
        Path base = toPath(project.getBasePath());
        Set<Path> result = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension compiler = CompilerModuleExtension.getInstance(module);
            if (compiler != null && compiler.getCompilerOutputUrl() != null) {
                Path output = toPath(VfsUtilCore.urlToPath(compiler.getCompilerOutputUrl()));
                if (output != null) {
                    result.add(output.resolve(REPORT).normalize());
                }
            }
            for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
                if (!root.isInLocalFileSystem()) {
                    continue;
                }
                Path dir = root.toNioPath();
                for (int level = 0; level <= PARENT_LEVELS && dir != null; level++, dir = dir.getParent()) {
                    if (base != null && !dir.startsWith(base)) {
                        break; // never look outside the project
                    }
                    for (String output : BUILD_OUTPUTS) {
                        result.add(dir.resolve(output).resolve(REPORT).normalize());
                    }
                }
            }
        }
        return result;
    }

    /** The output directory of a report, relative to the project when possible, for display. */
    static String label(Project project, Path report) {
        Path output = report;
        for (int i = 0; i < 3 && output.getParent() != null; i++) {
            output = output.getParent(); // strip META-INF/springgraph/graph.json
        }
        Path base = toPath(project.getBasePath());
        String label = base != null && output.startsWith(base)
                ? base.relativize(output).toString()
                : output.toString();
        return label.isEmpty() ? "." : label.replace('\\', '/');
    }

    private static Path toPath(String path) {
        if (path == null) {
            return null;
        }
        try {
            return Path.of(path).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            return null;
        }
    }
}
