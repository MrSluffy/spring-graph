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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Parses {@code graph.json}. Has no IntelliJ dependency, so it is tested without starting the platform. */
public final class ReportReader {

    /** The schema version this plugin understands. */
    public static final int SUPPORTED_SCHEMA = 1;

    private static final Gson GSON = new Gson();

    private ReportReader() {}

    public static ReportModel read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static ReportModel read(Reader reader) throws IOException {
        ReportModel model;
        try {
            model = GSON.fromJson(reader, ReportModel.class);
        } catch (JsonParseException ex) {
            throw new IOException("not a valid SpringGraph report: " + ex.getMessage(), ex);
        }
        if (model == null) {
            throw new IOException("the report is empty");
        }
        if (model.schemaVersion != SUPPORTED_SCHEMA) {
            throw new IOException(
                    model.schemaVersion > SUPPORTED_SCHEMA
                            ? "report schema " + model.schemaVersion + " is newer than this plugin supports ("
                                    + SUPPORTED_SCHEMA + "); update the SpringGraph plugin"
                            : "unsupported report schema " + model.schemaVersion);
        }
        return model;
    }
}
