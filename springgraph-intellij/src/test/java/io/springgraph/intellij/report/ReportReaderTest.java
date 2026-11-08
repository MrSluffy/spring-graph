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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;

/** Reads the processor's own snapshot (processor/full-advisory.json, copied by the build), so both sides agree. */
public class ReportReaderTest {

    public static ReportModel processorSnapshot() throws IOException {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(ReportReaderTest.class.getResourceAsStream("/processor/full-advisory.json")),
                StandardCharsets.UTF_8)) {
            return ReportReader.read(reader);
        }
    }

    @Test
    public void readsTheProcessorReport() throws IOException {
        ReportModel model = processorSnapshot();
        assertEquals(1, model.schemaVersion);
        assertEquals("advisory", model.mode);
        assertEquals(4, model.coverage.validated);
        assertEquals(15, model.coverage.total);

        ReportSnapshot snapshot = ReportSnapshot.of(
                java.util.List.of(new ReportSnapshot.Loaded(null, "app", model)), java.util.List.of());
        ReportModel.Component orders = snapshot.component("p.OrderService").component();
        assertEquals(5, orders.dependencies().size());
        ReportModel.Dependency gateway = orders.dependencies().get(0);
        assertEquals("gateway", gateway.parameter);
        assertEquals("p.Gateway", gateway.type);
        assertEquals(ReportModel.Resolution.STATIC, gateway.resolution());
        assertEquals(java.util.List.of("p.DefaultGateway"), gateway.targets());
        assertEquals(
                ReportModel.Resolution.MISSING, orders.dependencies().get(4).resolution());

        ReportModel.Finding missing =
                snapshot.findings(snapshot.component("p.OrderService"), 4).get(0);
        assertEquals("SG001", missing.code);
        assertEquals("No static binding found for p.Missing.", missing.summary());
        assertTrue(snapshot.findings(snapshot.component("p.OrderService"), 0).isEmpty());

        assertEquals("@Profile", snapshot.component("p.JpaStore").component().conditional);
        assertNull("excluded classes are not indexed as components", snapshot.component("p.Proto"));
        assertTrue(model.excluded().stream().anyMatch(e -> e.configuration && "p.Config".equals(e.name)));
    }

    @Test
    public void unknownFieldsAndResolutionsAreTolerated() throws IOException {
        ReportModel model = ReportReader.read(new StringReader("""
                {"schemaVersion": 1, "mode": "strict", "addedLater": true,
                 "components": [{"name": "p.A", "dependencies": [
                   {"index": 0, "type": "p.B", "resolution": "SOMETHING_NEW", "targets": []}]}]}
                """));
        assertTrue(model.isStrict());
        assertEquals(
                ReportModel.Resolution.UNKNOWN,
                model.components().get(0).dependencies().get(0).resolution());
        assertTrue(model.findings().isEmpty());
    }

    @Test
    public void rejectsANewerSchema() {
        IOException ex =
                assertThrows(IOException.class, () -> ReportReader.read(new StringReader("{\"schemaVersion\": 2}")));
        assertTrue(ex.getMessage(), ex.getMessage().contains("update the SpringGraph plugin"));
    }

    @Test
    public void rejectsInvalidJson() {
        assertThrows(IOException.class, () -> ReportReader.read(new StringReader("{not json")));
        assertThrows(IOException.class, () -> ReportReader.read(new StringReader("")));
    }
}
