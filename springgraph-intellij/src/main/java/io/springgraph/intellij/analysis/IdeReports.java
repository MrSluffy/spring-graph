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
package io.springgraph.intellij.analysis;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.zip.CRC32;

/**
 * Where the IDE analysis keeps its files: in IntelliJ's system directory, never in the project or its build output, so
 * the analysis cannot interfere with the build or end up in version control.
 */
public final class IdeReports {

    private IdeReports() {}

    /** The extracted bundled processor, shared by all projects. */
    public static Path processorCache() {
        return Path.of(PathManager.getSystemPath(), "springgraph", "processor");
    }

    /** The working directory for one module's analysis. */
    public static Path workDir(Project project, String moduleName) {
        return Path.of(PathManager.getSystemPath(), "springgraph", "projects", project.getLocationHash())
                .resolve(dirName(moduleName));
    }

    /** The report the IDE analysis of a module writes. */
    public static Path report(Project project, String moduleName) {
        return workDir(project, moduleName).resolve("classes/META-INF/springgraph/graph.json");
    }

    /** A safe, unique directory name: readable characters of the module name plus a hash of the full name. */
    static String dirName(String moduleName) {
        String readable = moduleName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (readable.length() > 60) {
            readable = readable.substring(0, 60);
        }
        CRC32 crc = new CRC32();
        crc.update(moduleName.getBytes(StandardCharsets.UTF_8));
        return readable + "-" + HexFormat.of().toHexDigits((int) crc.getValue());
    }
}
