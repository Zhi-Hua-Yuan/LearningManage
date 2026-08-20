package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayPublishedMigrationImmutabilityTest {

    private static final String MANIFEST_RESOURCE = "flyway/published-migrations.sha256";

    @Test
    void v1MatchesTheFrozenPublishedHash() throws IOException {
        assertEquals(FlywayTestSupport.EXPECTED_V1_SHA256,
                FlywayTestSupport.sha256(FlywayTestSupport.readResourceBytes(FlywayTestSupport.V1_RESOURCE)));
    }

    @Test
    void everyPublishedMigrationMatchesItsManifestAndEveryMigrationIsRegistered() throws IOException {
        Map<String, String> manifest = FlywayTestSupport.parsePublishedMigrationManifest(
                FlywayTestSupport.readResourceText(MANIFEST_RESOURCE));
        Path migrationDirectory = FlywayTestSupport.projectRoot()
                .resolve("src/main/resources/db/migration");

        List<Path> migrationFiles;
        try (var paths = Files.list(migrationDirectory)) {
            migrationFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        assertFalse(migrationFiles.isEmpty());
        assertEquals(migrationFiles.size(), manifest.size());

        Set<Integer> versions = new HashSet<>();
        Pattern migrationPattern = FlywayTestSupport.migrationFilePattern();
        for (Path migrationFile : migrationFiles) {
            String relativePath = FlywayTestSupport.projectRoot().relativize(migrationFile)
                    .toString().replace('\\', '/');
            String expectedHash = manifest.get(relativePath);
            assertTrue(expectedHash != null, "migration is missing from published manifest: " + relativePath);

            var matcher = migrationPattern.matcher(migrationFile.getFileName().toString());
            assertTrue(matcher.matches(), "invalid versioned migration filename: " + relativePath);
            assertTrue(versions.add(Integer.parseInt(matcher.group(1))),
                    "duplicate migration version: " + matcher.group(1));
            assertEquals(expectedHash, FlywayTestSupport.sha256(Files.readAllBytes(migrationFile)),
                    "published migration hash mismatch: " + relativePath);
        }

        for (String manifestPath : manifest.keySet()) {
            assertTrue(Files.isRegularFile(FlywayTestSupport.projectRoot().resolve(manifestPath)),
                    "manifest points to a missing migration: " + manifestPath);
        }
    }

    @Test
    void malformedOrDuplicateManifestEntriesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FlywayTestSupport.parsePublishedMigrationManifest("not-a-hash  file.sql"));
        assertThrows(IllegalArgumentException.class,
                () -> FlywayTestSupport.parsePublishedMigrationManifest(
                        "A".repeat(64) + "  src/main/resources/db/migration/V1__baseline_schema.sql\n"
                                + "B".repeat(64) + "  src/main/resources/db/migration/V1__baseline_schema.sql"));
        assertThrows(IllegalArgumentException.class,
                () -> FlywayTestSupport.parsePublishedMigrationManifest(
                        "A".repeat(64) + "  docs/other.sql"));
    }

    @Test
    void aChangedByteInV1DoesNotMatchThePublishedHash() throws IOException {
        byte[] changed = FlywayTestSupport.readResourceBytes(FlywayTestSupport.V1_RESOURCE);
        changed[0] = (byte) (changed[0] ^ 1);
        assertNotEquals(FlywayTestSupport.EXPECTED_V1_SHA256, FlywayTestSupport.sha256(changed));
    }
}
