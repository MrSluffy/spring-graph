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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/**
 * The published {@code io.github.mrsluffy:springgraph-processor} jar shipped inside the plugin. The build verifies it
 * against the pinned SHA-256 (gradle.properties); here it is extracted once into a cache directory and verified again
 * before every use, so a tampered or truncated copy is never run. Nothing is downloaded.
 */
public final class BundledProcessor {

    private static final String JAR = "/springgraph/springgraph-processor.jar";
    private static final String PROPERTIES = "/springgraph/processor.properties";

    private final String version;
    private final String sha256;

    private BundledProcessor(String version, String sha256) {
        this.version = version;
        this.sha256 = sha256;
    }

    /** Reads the version and hash recorded by the build. */
    public static BundledProcessor load() {
        Properties properties = new Properties();
        try (InputStream in = BundledProcessor.class.getResourceAsStream(PROPERTIES)) {
            if (in == null) {
                throw new IllegalStateException(
                        "The plugin was built without its bundled processor (" + PROPERTIES + ")");
            }
            properties.load(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new BundledProcessor(properties.getProperty("version"), properties.getProperty("sha256"));
    }

    /** The processor version, e.g. {@code 0.1.0-rc.4}. */
    public String version() {
        return version;
    }

    public String sha256() {
        return sha256;
    }

    /**
     * The verified jar inside {@code cacheDir}, extracted first if it is missing or does not match the recorded hash.
     */
    public synchronized Path extractTo(Path cacheDir) throws IOException {
        Path target = cacheDir.resolve("springgraph-processor-" + version + ".jar");
        if (Files.isRegularFile(target) && sha256.equals(sha256(target))) {
            return target;
        }
        Files.createDirectories(cacheDir);
        Path temp = Files.createTempFile(cacheDir, "springgraph-processor-", ".tmp");
        try {
            try (InputStream in = BundledProcessor.class.getResourceAsStream(JAR)) {
                if (in == null) {
                    throw new IOException("The plugin was built without its bundled processor (" + JAR + ")");
                }
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String actual = sha256(temp);
            if (!sha256.equals(actual)) {
                throw new IOException("Bundled processor is corrupt: SHA-256 " + actual + ", expected " + sha256);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
