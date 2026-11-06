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

## Database initialization

Create an empty MySQL 8.x database, then select it in the mysql client:

```sql
CREATE DATABASE bluemoon_db CHARACTER SET utf8mb4;
USE bluemoon_db;
SOURCE database/schema.sql;
SOURCE database/seed.sql;
```

The schema adds only `users` (EP-06), not household/apartment tables. It contains
one role enum and an extra UTC `locked_until` timestamp to persist the timed lock
across clients/restarts. No separate account_roles or permissions schema is added.
The inherited database collation controls username comparison/case sensitivity.
Use MySQL 8.0.16+ for enforced CHECK constraints (verified here on 8.4.13).

Seed is for local/QA only. It creates the **public SRS fixture** `admin` /
`admin@123`, display name Admin, role ADMIN. SQL contains only its BCrypt cost-12
hash. Do not provision this public fixture in production. Rerunning the seed
does not change existing passwords, roles or lock state. No real database
credentials are committed. Account creation/password changes are outside US-012.

See [the implemented API](../design/auth-api-proposal.md) to call login/logout
from Dev-2's controller. The placeholder does not authenticate by itself.

## MySQL integration tests

Prepare an **isolated** database whose name ends in `_test`, configure its
credentials via the variables/file above, then run:

```powershell
$env:BLUEMOON_MYSQL_TEST = '1'
mvn -Pmysql-it verify
```

The suite verifies MySQL 8.x, loads schema/seed and creates/removes uniquely named
test users. It also authenticates the public admin seed, resetting its login
state on success; never point it at a shared developer/production database.
Default `mvn clean verify` does not need MySQL or a desktop. See
[test evidence](../test/README.md) for the exact combined verification command.

## Local verification tools

This session downloaded portable Maven/JDK into ignored `.tools/` and uses the
ignored `.m2-local/` cache. These are not required or committed for teammates.
Use your installed JDK 17/Maven. The build rejects JDK versions outside 17.x.

JavaFX Maven setup follows [OpenJFX documentation](https://openjfx.io/openjfx-docs/).
Connector coordinates follow [MySQL documentation](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html).
