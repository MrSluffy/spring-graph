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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public void extractionUsesTheSameLockAcrossInstances() throws Exception {
        Path cache = temp.getRoot().toPath();
        var executor = Executors.newFixedThreadPool(2);
        var started = new CountDownLatch(2);
        try {
            java.util.concurrent.Future<Path> first;
            java.util.concurrent.Future<Path> second;
            synchronized (BundledProcessor.class) {
                first = executor.submit(() -> {
                    BundledProcessor processor = BundledProcessor.load();
                    started.countDown();
                    return processor.extractTo(cache);
                });
                second = executor.submit(() -> {
                    BundledProcessor processor = BundledProcessor.load();
                    started.countDown();
                    return processor.extractTo(cache);
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> first.get(100, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
                try (var files = Files.list(cache)) {
                    assertEquals(0, files.count());
                }
            }
            Path jar = first.get(5, TimeUnit.SECONDS);
            assertEquals(jar, second.get(5, TimeUnit.SECONDS));
            assertEquals(BundledProcessor.load().sha256(), BundledProcessor.sha256(jar));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
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
