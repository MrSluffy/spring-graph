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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.springgraph.intellij.report.ReportModel;
import io.springgraph.intellij.report.ReportReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Runs the bundled, published processor through a real javac process, as the IDE analysis does. */
public class JavacRunnerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private Path processor;
    private Path sources;
    private Path workDir;

    @Before
    public void setUp() throws IOException {
        processor =
                BundledProcessor.load().extractTo(temp.newFolder("processor").toPath());
        // Spaces and non-ASCII in every path the argument file carries.
        Path root = temp.newFolder("my project ö").toPath();
        sources = root.resolve("src main");
        workDir = root.resolve("work dir");
        Files.createDirectories(workDir);
        // The processor matches Spring annotations by name, so a stub is enough.
        write(
                "org/springframework/stereotype/Service.java",
                "package org.springframework.stereotype; public @interface Service {}");
        write("p/Missing.java", "package p; public interface Missing {}");
        write("p/OrderService.java", """
                package p;
                @org.springframework.stereotype.Service
                public class OrderService { public OrderService(Missing missing) {} }
                """);
    }

    @Test
    public void strictFindingsFailJavacButTheReportIsWritten() throws Exception {
        JavacRunner.Result result = JavacRunner.run(request("strict", ""), 120, () -> false);
        assertEquals(result.output(), 1, result.exitCode());
        assertNotNull(result.output(), result.report());
        ReportModel model = ReportReader.read(result.report());
        assertEquals("strict", model.mode);
        ReportModel.Finding finding = model.findings().get(0);
        assertEquals("SG001", finding.code);
        assertTrue(finding.isError());
        assertEquals("p.OrderService", finding.component);
        assertEquals(Integer.valueOf(0), finding.parameter);
        assertTrue(result.output(), result.output().contains("SG001"));
    }

    @Test
    public void advisoryPassesAndBoundariesAreApplied() throws Exception {
        JavacRunner.Result advisory = JavacRunner.run(request("advisory", ""), 120, () -> false);
        assertEquals(advisory.output(), 0, advisory.exitCode());
        assertFalse(ReportReader.read(advisory.report()).findings().get(0).isError());

        JavacRunner.Result bounded = JavacRunner.run(request("strict", "p.Missing"), 120, () -> false);
        assertEquals(bounded.output(), 0, bounded.exitCode());
        ReportModel model = ReportReader.read(bounded.report());
        assertEquals(
                ReportModel.Resolution.BOUNDARY,
                model.components().get(0).dependencies().get(0).resolution());
        assertTrue(model.findings().isEmpty());
    }

    @Test
    public void eachRunReplacesThePreviousOutput() throws Exception {
        JavacRunner.Result first = JavacRunner.run(request("advisory", ""), 120, () -> false);
        Path stale = first.report().resolveSibling("stale.txt");
        Files.writeString(stale, "from an earlier run");
        JavacRunner.run(request("advisory", ""), 120, () -> false);
        assertFalse(Files.exists(stale));
    }

    @Test
    public void noReportWhenJavacCannotStartProcessing() throws Exception {
        Files.writeString(sources.resolve("p/Broken.java"), "package p; public class Broken {");
        JavacRunner.Result result = JavacRunner.run(request("advisory", ""), 120, () -> false);
        assertNotEquals(0, result.exitCode());
        assertNull(result.report());
        assertTrue(result.output(), result.output().contains("Broken.java"));
    }

    @Test
    public void cancellationStopsJavac() {
        assertThrows(InterruptedException.class, () -> JavacRunner.run(request("advisory", ""), 120, () -> true));
    }

    @Test
    public void argumentFileQuotesEveryValue() {
        assertEquals("\"a b\"", JavacRunner.quote("a b"));
        assertEquals("\"say \\\"hi\\\" C:\\\\x\"", JavacRunner.quote("say \"hi\" C:\\x"));
        assertEquals("\"Gr\u00f6\u00dfe\"", JavacRunner.quote("Gr\u00f6\u00dfe"));
        assertThrows(IllegalArgumentException.class, () -> JavacRunner.quote("a\nb"));
        // A boundary value cannot smuggle in another option: it stays one quoted argument.
        List<String> lines = JavacRunner.argumentFileLines(request("advisory", "x\" -processor \"evil"));
        assertTrue(lines.contains("\"-Aspringgraph.runtimeBoundaries=x\\\" -processor \\\"evil\""));
    }

    @Test
    public void recognisesSpringGraphOnTheProjectsProcessorPath() throws IOException {
        assertTrue(ModuleAnalysisPlanner.isSpringGraphProcessor(Path.of("libs/springgraph-processor-0.1.0-rc.3.jar")));
        assertFalse(ModuleAnalysisPlanner.isSpringGraphProcessor(Path.of("libs/lombok-1.18.48.jar")));
        Path classes = temp.newFolder("classes").toPath();
        Files.createDirectories(classes.resolve("io/springgraph/processor"));
        Files.writeString(classes.resolve("io/springgraph/processor/SpringGraphProcessor.class"), "");
        assertTrue(ModuleAnalysisPlanner.isSpringGraphProcessor(classes));
    }

    private JavacRequest request(String mode, String boundaries) {
        try (var walk = Files.walk(sources)) {
            List<Path> files =
                    walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            return new JavacRequest(
                    javac(),
                    files,
                    List.of(),
                    List.of(sources),
                    List.of(processor),
                    "UTF-8",
                    mode,
                    boundaries,
                    workDir);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private void write(String path, String content) throws IOException {
        Path file = sources.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** The test JVM is the build's JDK 21 toolchain. */
    private static Path javac() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path exe = bin.resolve("javac.exe");
        return Files.isRegularFile(exe) ? exe : bin.resolve("javac");
    }

    private static void assertNotEquals(int unexpected, int actual) {
        assertFalse("expected not " + unexpected, unexpected == actual);
    }
}
