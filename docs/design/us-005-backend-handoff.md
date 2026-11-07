# US-005 Backend — Mốc 2 handoff

- Branch: `feature/us005-fee-category-backend`.
- Base: `c9aeab0e432e9f9e4487fc7a944e82b93dd75b7e` (`origin/main`, shared contract/UI merge).
- Pre-flight: clean working tree; fetched origin; merge-base ancestor check returned 0; all seven shared types verified. Backend branch did not previously exist.
- Public interface, DTOs, enums, UI, HouseholdService and AuthService are unchanged.

## Implementation

New files:

1. `database/migrations/us005_fee_types.sql`
2. `src/main/java/com/bluemoon/dao/FeeTypeRepository.java`
3. `src/main/java/com/bluemoon/dao/FeeTypeDAO.java`
4. `src/main/java/com/bluemoon/services/FeeTypeServiceImpl.java`
5. `src/test/java/com/bluemoon/dao/FeeTypeDAOTest.java`
6. `src/test/java/com/bluemoon/dao/FeeTypeMySqlIT.java`
7. `src/test/java/com/bluemoon/services/FeeTypeServiceTest.java`
8. This handoff.

Service implements `listFeeTypes`, `getFeeType`, `createFeeType`, `updateFeeType`, `deactivateFeeType` without contract changes. Null list filter means all, ordered by code. Missing rows return NOT_FOUND. Null forms and invalid inputs return fieldErrors using the approved SRS keys. Blank optional amounts become zero; malformed decimals, negatives, exponent notation, more than 17 integer digits or 2 fractional digits are rejected without rounding. DONG_GOP requires TU_NGUYEN and zero. Update rejects code changes.

Authorization delegates to existing US-018 AuthorizationService: VIEW for list/get, CREATE for create, UPDATE for update/deactivate. Existing production RBAC permits ADMIN/ACCOUNTANT and denies RESIDENT/no session; checks precede validation and repository access. Production construction: `new FeeTypeServiceImpl(new FeeTypeDAO(databaseConnection))`; an existing AuthorizationService can also be injected. No application navigation wiring is included.

JDBC uses prepared statements and fresh connections from DatabaseConnection. Mutations explicitly commit/rollback; update/deactivate and the returned snapshot share a transaction. MySQL 1062 maps to DUPLICATE on create; other SQL failures map to generic SYSTEM_ERROR. Rollback failures are suppressed on the original exception. There is no repository delete method or SQL DELETE.

## Migration

Apply `database/migrations/us005_fee_types.sql` separately on MySQL 8.0.16+ (enforced CHECK support), with strict SQL mode enabled. Foundation schema is untouched. Migration creates only fee_types; no seed data or automatic migration runner is added.

The database column names follow the Mốc 2 order; public DTO/error keys retain the approved contract:

| DTO accessor | Database column | fieldErrors key |
| --- | --- | --- |
| maKhoanThu | ma_loai_thu | ma_khoan_thu |
| tenKhoanThu | ten_loai_thu | ten_khoan_thu |
| loai | loai_thu | loai_khoan_thu |
| hinhThuc | phuong_thuc_tinh | hinh_thuc_thu |
| soTienMacDinh | so_tien_mac_dinh | so_tien_mac_dinh |
| trangThai | trang_thai | trang_thai |

Code is ASCII/case-sensitive VARCHAR(20), primary key, checked against uppercase letters/digits/underscore. Name is VARCHAR(100), checked for trimmed length 3–100. Amount is DECIMAL(19,2), nonnegative, default zero. Enum and kind/method/amount consistency are constrained. No hard deletion is implemented. Applying this migration does not grant or revoke database privileges.

## Verification

JDK Microsoft 17.0.21.0.2; Apache Maven 3.9.9. Maven Enforcer enabled and passed.

- `mvn clean verify`: PASS, 238 tests, 0 failures/errors/skips.
- `mvn -Pjavafx-smoke clean verify`: PASS, 239 tests, 0 failures/errors/skips.
- `git diff --check`: PASS.
- New default tests: 32 service cases plus 6 JDBC transaction/failure tests. Tests use real existing RBAC implementation with test principals; no fake production ADMIN.

MySQL IT **not run**: no `config/db.properties` / configured dedicated test database in this workspace. No claim of real-DB verification. The opt-in tests compile in the normal build but are excluded from default execution. They cover migration, round-trip DECIMAL, duplicate 1062, rollback after invalid writes, filtering, unchanged update, repeated deactivate, retained rows, constraints, and SQL injection literals.

To run on a dedicated disposable MySQL schema ending `_test`, configure DatabaseConfig as documented in developer setup, set `BLUEMOON_MYSQL_TEST=1`, then run `mvn -Pmysql-it -Dtest=FeeTypeMySqlIT verify`. The test drops/recreates only fee_types in that dedicated schema. Do not target a shared database. Real MySQL validation remains for QA/Lead before acceptance; no dependency on invoice tables or Dashboard integration is introduced.

Git commit identifier is supplied in the delivery message and PR (avoids a self-referential commit hash in this file). Branch is handed off for review, not merged into main.
