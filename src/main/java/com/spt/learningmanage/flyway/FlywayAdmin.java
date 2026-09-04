package com.spt.learningmanage.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Set;

/**
 * One-shot, non-web Flyway administration entry point.
 *
 * <p>All database credentials are read from environment variables. The
 * application datasource credentials are deliberately not used here.</p>
 */
public final class FlywayAdmin {

    private static final Set<String> ACTIONS = Set.of("info", "validate", "baseline", "migrate");
    private static final String EXPECTED_BASELINE_VERSION = "1";

    private FlywayAdmin() {
    }

    public static void main(String[] args) {
        try {
            String action = parseAction(args);
            Map<String, String> environment = System.getenv();
            if ("baseline".equals(action)) {
                requireBaselineAuthorization(environment);
                baselineLegacySchema(environment);
                return;
            }

            var configuration = Flyway.configure()
                    .dataSource(jdbcUrl(environment), required(environment, "FLYWAY_DB_USERNAME"),
                            required(environment, "FLYWAY_DB_PASSWORD"))
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .validateOnMigrate(true)
                    .cleanDisabled(true)
                    .outOfOrder(false)
                    .baselineVersion(EXPECTED_BASELINE_VERSION);
            String targetVersion = optionalTargetVersion(environment);
            if (targetVersion != null) {
                configuration.target(targetVersion);
            }
            if ("validate".equals(action)
                    && "true".equalsIgnoreCase(environment.get("FLYWAY_ALLOW_PENDING_VALIDATE"))) {
                configuration.ignoreMigrationPatterns("*:pending");
            }
            Flyway flyway = configuration.load();

            switch (action) {
                case "info" -> printInfo(flyway.info());
                case "validate" -> {
                    flyway.validate();
                    System.out.println("validate.success=true");
                }
                case "baseline" -> {
                    var result = flyway.baseline();
                    System.out.println("baseline.baselineVersion=" + result.baselineVersion);
                    System.out.println("baseline.success=true");
                }
                case "migrate" -> {
                    var result = flyway.migrate();
                    System.out.println("migrate.success=" + result.success);
                    System.out.println("migrate.migrationsExecuted=" + result.migrationsExecuted);
                    System.out.println("migrate.targetSchemaVersion=" + result.targetSchemaVersion);
                }
                default -> throw new IllegalStateException("unsupported action");
            }
        } catch (Exception exception) {
            System.err.println("flyway.error=" + sanitize(exception));
            System.exit(1);
        }
    }

    static String parseAction(String[] args) {
        if (args == null || args.length != 1 || !ACTIONS.contains(args[0])) {
            throw new IllegalArgumentException("usage: info|validate|baseline|migrate");
        }
        return args[0];
    }

    static void requireBaselineAuthorization(Map<String, String> environment) {
        if (!"true".equalsIgnoreCase(environment.get("FLYWAY_BASELINE_AUTHORIZED"))) {
            throw new IllegalStateException("baseline requires FLYWAY_BASELINE_AUTHORIZED=true");
        }
        String targetDatabase = environment.get("DB_NAME");
        if (targetDatabase == null || targetDatabase.isBlank()
                || !targetDatabase.equals(environment.get("FLYWAY_EXPECTED_DB_NAME"))) {
            throw new IllegalStateException("baseline requires FLYWAY_EXPECTED_DB_NAME to equal DB_NAME");
        }
        if (!EXPECTED_BASELINE_VERSION.equals(environment.get("FLYWAY_BASELINE_VERSION"))) {
            throw new IllegalStateException("baseline requires FLYWAY_BASELINE_VERSION=1");
        }
    }

    static String optionalTargetVersion(Map<String, String> environment) {
        String value = environment.get("FLYWAY_TARGET_VERSION");
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("FLYWAY_TARGET_VERSION must be a positive integer");
        }
        return value;
    }

    private static void baselineLegacySchema(Map<String, String> environment) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(environment), required(environment, "FLYWAY_DB_USERNAME"),
                required(environment, "FLYWAY_DB_PASSWORD"))) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name <> 'flyway_schema_history'")) {
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) == 0) {
                        throw new IllegalStateException("baseline requires a non-empty schema");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")) {
                try (var result = statement.executeQuery()) {
                    if (result.next() && result.getInt(1) != 0) {
                        throw new IllegalStateException("baseline history table already exists");
                    }
                }
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE `flyway_schema_history` ("
                        + "`installed_rank` INT NOT NULL,"
                        + "`version` VARCHAR(50),"
                        + "`description` VARCHAR(200) NOT NULL,"
                        + "`type` VARCHAR(20) NOT NULL,"
                        + "`script` VARCHAR(1000) NOT NULL,"
                        + "`checksum` INT,"
                        + "`installed_by` VARCHAR(100) NOT NULL,"
                        + "`installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "`execution_time` INT NOT NULL,"
                        + "`success` TINYINT NOT NULL,"
                        + "CONSTRAINT `flyway_schema_history_pk` PRIMARY KEY (`installed_rank`)"
                        + ") ENGINE=InnoDB");
                statement.executeUpdate("CREATE INDEX `flyway_schema_history_s_idx` "
                        + "ON `flyway_schema_history` (`success`)");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO `flyway_schema_history` "
                            + "(`installed_rank`, `version`, `description`, `type`, `script`, "
                            + "`checksum`, `installed_by`, `execution_time`, `success`) "
                            + "VALUES (1, ?, '<< Flyway Baseline >>', 'BASELINE', "
                            + "'<< Flyway Baseline >>', NULL, CURRENT_USER(), 0, 1)")) {
                statement.setString(1, EXPECTED_BASELINE_VERSION);
                statement.executeUpdate();
            }
        }
        System.out.println("baseline.baselineVersion=" + EXPECTED_BASELINE_VERSION);
        System.out.println("baseline.success=true");
    }

    private static String jdbcUrl(Map<String, String> environment) {
        String host = required(environment, "DB_HOST");
        String port = required(environment, "DB_PORT");
        String database = required(environment, "DB_NAME");
        if (!database.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("DB_NAME contains unsupported characters");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=utf8";
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static void printInfo(MigrationInfoService info) {
        System.out.println("info.current=" + (info.current() == null ? "<none>" : info.current().getVersion()));
        for (MigrationInfo migration : info.all()) {
            System.out.println("info.migration=" + migration.getVersion() + "|"
                    + migration.getDescription() + "|" + migration.getState());
        }
    }

    private static String sanitize(Exception exception) {
        String message = exception.getMessage() == null ? "<no message>" : exception.getMessage();
        return exception.getClass().getSimpleName() + ": "
                + message.replaceAll("(?i)password=[^&\\s]*", "password=<redacted>");
    }
}
