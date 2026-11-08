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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.Test;

/** Module files come from the repository: their paths must never make the plugin probe outside the project. */
public class ReportLocatorTest {

    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"), "project")
            .toAbsolutePath()
            .normalize();

    @Test
    public void acceptsPathsInsideTheProject() {
        assertTrue(ReportLocator.insideProject(BASE, BASE.resolve("build/classes/java/main")));
        assertTrue(ReportLocator.insideProject(BASE, BASE));
    }

    @Test
    public void rejectsPathsOutsideTheProject() {
        assertFalse(ReportLocator.insideProject(BASE, BASE.resolve("../other/build")));
        assertFalse(ReportLocator.insideProject(BASE, BASE.resolveSibling("project-evil")));
        assertFalse(ReportLocator.insideProject(BASE, BASE.getRoot()));
        assertFalse("nothing is probed without a project directory", ReportLocator.insideProject(null, BASE));
    }

    @Test
    public void rejectsNetworkShares() {
        if (File.separatorChar == '\\') {
            assertFalse(ReportLocator.insideProject(BASE, Path.of("\\\\attacker.example\\share\\out")));
        }
    }
}
