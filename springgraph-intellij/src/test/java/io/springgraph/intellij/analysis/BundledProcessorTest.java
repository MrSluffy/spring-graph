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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BundledProcessorTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void bundlesThePinnedPublishedRelease() {
        BundledProcessor processor = BundledProcessor.load();
        assertEquals(System.getProperty("springgraph.bundledProcessorVersion", "0.1.0-rc.4"), processor.version());
        assertEquals(64, processor.sha256().length());
    }

    @Test
    public void extractsAVerifiedJarThatWritesTheJsonReport() throws IOException {
        BundledProcessor processor = BundledProcessor.load();
        Path jar = processor.extractTo(temp.getRoot().toPath());
        assertEquals(processor.sha256(), BundledProcessor.sha256(jar));
        try (JarFile file = new JarFile(jar.toFile())) {
            assertNotNull(file.getEntry("io/springgraph/processor/SpringGraphProcessor.class"));
            assertNotNull(
                    "graph.json needs 0.1.0-rc.4 or newer",
                    file.getEntry("io/springgraph/processor/internal/report/JsonReport.class"));
            assertNotNull(file.getEntry("META-INF/services/javax.annotation.processing.Processor"));
        }
    }

    @Test
    public void replacesATamperedCopy() throws IOException {
        BundledProcessor processor = BundledProcessor.load();
        Path jar = processor.extractTo(temp.getRoot().toPath());
        Files.writeString(jar, "not the processor");
        Path again = processor.extractTo(temp.getRoot().toPath());
        assertEquals(jar, again);
        assertEquals(processor.sha256(), BundledProcessor.sha256(again));
    }

    @Test
    public void reusesAVerifiedCopy() throws IOException {
        BundledProcessor processor = BundledProcessor.load();
        Path jar = processor.extractTo(temp.getRoot().toPath());
        long modified = Files.getLastModifiedTime(jar).toMillis();
        assertEquals(jar, processor.extractTo(temp.getRoot().toPath()));
        assertEquals(modified, Files.getLastModifiedTime(jar).toMillis());
        try (var files = Files.list(temp.getRoot().toPath())) {
            assertTrue(
                    "no temporary files left", files.allMatch(p -> p.toString().endsWith(".jar")));
        }
    }
}
