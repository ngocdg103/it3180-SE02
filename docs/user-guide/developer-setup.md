# BlueMoon developer setup — Foundation

## Requirements

- JDK **17**, with `JAVA_HOME` pointing to it (JDK 25 is not the team baseline).
- Maven 3.9.x on PATH.
- MySQL **8.x** for database work.
- Scene Builder for Dev-2 FXML editing; JavaFX is resolved by Maven.

From the repository root:

```powershell
java -version
mvn -version
mvn clean verify
mvn javafx:run
```

Opt-in FXML/window startup check on a desktop session:
`mvn -Pjavafx-smoke verify`. It opens and automatically closes the placeholder.
Default tests do not launch a GUI.

The app currently opens a minimal Foundation FXML view. It does not contain
Dev-2's login form and makes no database request at startup.

## External database configuration

```powershell
Copy-Item config/db.properties.example config/db.properties
```

Fill `db.url`, `db.username`, `db.password` in that ignored local file. Example
URL: `jdbc:mysql://localhost:3306/bluemoon_db?connectionTimeZone=UTC`.
Use your own local credentials, never put them into tracked files.
MySQL JDBC discovers its driver automatically; no driver or pool settings are
required. Each `getConnection()` call opens one connection; the DAO owns its
closure via try-with-resources. Connect/socket timeouts are 5/10 seconds.

Supported environment overrides:

| Variable | Meaning |
| --- | --- |
| `BLUEMOON_DB_CONFIG` | Alternate local properties file path |
| `BLUEMOON_DB_URL` | Overrides `db.url` |
| `BLUEMOON_DB_USERNAME` | Overrides `db.username` |
| `BLUEMOON_DB_PASSWORD` | Overrides `db.password` |

Environment-only configuration is supported. Empty required values fail rather
than silently falling back. Configuration values are not printed by the app.

## Database initialization status

`database/schema.sql` and `database/seed.sql` are still the original placeholders.
Do not treat them as an operational authentication database. Account/role tables,
BCrypt seed and MySQL login verification await the missing SRS and lock rule
clarification. No database credentials or instance were supplied for this run.

## Local verification tools

This session downloaded portable Maven/JDK into ignored `.tools/` and uses the
ignored `.m2-local/` cache. These are not required or committed for teammates.
Use your installed JDK 17/Maven. The build rejects JDK versions outside 17.x.

JavaFX Maven setup follows [OpenJFX documentation](https://openjfx.io/openjfx-docs/).
Connector coordinates follow [MySQL documentation](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html).
