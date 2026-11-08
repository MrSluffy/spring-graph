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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JSON layout itself is locked by SnapshotTest (advisory). This covers what compile-testing cannot show: in strict
 * mode the build fails, and tools such as the IntelliJ plugin still need the report of that build.
 */
class JsonReportTest {

    @Test
    void strictFailureStillWritesJsonReportWithErrors(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src/p");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Missing.java"), "package p; public interface Missing {}");
        Files.writeString(src.resolve("OrderService.java"), """
                package p;
                @org.springframework.stereotype.Service
                public class OrderService { public OrderService(Missing missing) {} }
                """);
        Path out = dir.resolve("classes");
        Files.createDirectories(out);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        boolean success;
        try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            success = javac.getTask(
                            null,
                            files,
                            diagnostic -> {},
                            List.of(
                                    "-d",
                                    out.toString(),
                                    "-classpath",
                                    System.getProperty("java.class.path"),
                                    "-proc:only",
                                    "-processor",
                                    SpringGraphProcessor.class.getName(),
                                    "-Aspringgraph.mode=strict"),
                            null,
                            files.getJavaFileObjects(
                                    src.resolve("Missing.java").toFile(),
                                    src.resolve("OrderService.java").toFile()))
                    .call();
        }

        assertFalse(success, "strict mode must fail on a missing binding");
        Path json = out.resolve(SpringGraphProcessor.JSON_REPORT_PATH);
        assertTrue(Files.exists(json), "graph.json must be written even when the build fails");
        String report = Files.readString(json);
        assertTrue(report.contains("\"mode\": \"strict\""), report);
        assertTrue(report.contains("\"resolution\": \"MISSING\""), report);
        assertTrue(
                report.contains("\"code\": \"SG001\",\n      \"severity\": \"ERROR\",\n"
                        + "      \"component\": \"p.OrderService\",\n      \"parameter\": 0,"),
                report);
    }
}
