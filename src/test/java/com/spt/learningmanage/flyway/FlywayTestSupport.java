package com.spt.learningmanage.flyway;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FlywayTestSupport {

    static final String V1_RESOURCE = "db/migration/V1__baseline_schema.sql";
    static final String LEGACY_FIXTURE_RESOURCE = "db/legacy/pre_flyway_v1_schema.sql";
    static final String EXPECTED_V1_SHA256 =
            "E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9";
    static final String EXPECTED_LEGACY_FIXTURE_SHA256 =
            "1ECF286291C3276585DA18722348BC4D70FAC8B751C0563568CC4B58B417FF96";

    static final List<String> V1_TABLES = List.of(
            "user", "tenant", "role", "permission", "role_permission", "user_role",
            "team", "team_member", "project", "milestone", "task", "weekly_review",
            "prompt_template", "ai_call_log", "ai_draft", "ai_draft_confirm_log",
            "ai_replan_operation", "ai_replan_item", "task_status_idempotency",
            "task_title_rename_log"
    );

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?is)CREATE TABLE `([^`]+)`\\s*\\(.*?\\) ENGINE=.*?;");
    private static final Pattern MIGRATION_FILE_PATTERN = Pattern.compile(
            "V([1-9][0-9]*)__([A-Za-z0-9_]+)\\.sql");
    private static final Pattern MANIFEST_LINE_PATTERN = Pattern.compile(
            "^([0-9A-Fa-f]{64})\\s{2}(.+)$");

    private FlywayTestSupport() {
    }

    static byte[] readResourceBytes(String resource) throws IOException {
        try (InputStream inputStream = new ClassPathResource(resource).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    static String readResourceText(String resource) throws IOException {
        return new String(readResourceBytes(resource), StandardCharsets.UTF_8);
    }

    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02X", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static List<String> createTableNames(String sql) {
        Matcher matcher = Pattern.compile("CREATE TABLE `([^`]+)`").matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    static Map<String, String> createTableBlocks(String sql) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        Map<String, String> tables = new LinkedHashMap<>();
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (tables.put(tableName, matcher.group()) != null) {
                throw new IllegalArgumentException("duplicate CREATE TABLE block: " + tableName);
            }
        }
        return tables;
    }

    static Map<String, String> parsePublishedMigrationManifest(String manifest) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String rawLine : manifest.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = MANIFEST_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid published migration manifest line: " + rawLine);
            }
            String hash = matcher.group(1).toUpperCase();
            String path = matcher.group(2).trim().replace('\\', '/');
            if (!path.startsWith("src/main/resources/db/migration/")) {
                throw new IllegalArgumentException("manifest path is outside migration directory: " + path);
            }
            if (entries.put(path, hash) != null) {
                throw new IllegalArgumentException("duplicate published migration path: " + path);
            }
        }
        return entries;
    }

    static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate project root from working directory");
    }

    static Pattern migrationFilePattern() {
        return MIGRATION_FILE_PATTERN;
    }
}
