# Initial fee rate history hotfix

## Cause and behavior

US-005 previously persisted the entered amount only in `fee_types.so_tien_mac_dinh`.
US-006 history and Billing's effective-date query read `fee_rates`, so neither saw
that amount. This was a persistence gap, not a missing display row.

Creating an active mandatory fee with a positive initial amount now requires an
explicit date selected by the user. This follows EP03 US-006's required effective
date; the requester confirmed this choice. No creation-date, month-start or seed
date is inferred. The date must be within MySQL's supported 1000–9999 year range.
An omitted/zero amount still creates an unpriced category with no rate row.
Voluntary contributions remain zero and have no initial rate.

`FeeTypeDAO` writes the category and initial `FeeRate` on one connection and one
transaction. It reuses the US-006 eligibility/row-lock/insert implementation,
including generated-key validation. Either both writes commit or both roll back.
`INITIAL` is stored in the real rate row's existing `ghi_chu` column; the existing
US-006 note column displays it without synthesizing history.

Duplicate category creation is rejected by the existing primary key. The existing
unique category/date constraint remains in force. Later price changes use US-006's
append-only create-rate operation. Category metadata updates do not create another
INITIAL or overwrite rate history. Billing's date-selection SQL and issued invoice
snapshots are unchanged. RBAC and CSS/theme files are unchanged.

## API compatibility and deployment

`FeeTypeForm` adds `ngayApDungBanDau`; the six-argument constructor remains for
unpriced creates and metadata updates. A positive create through an old caller
without a date now receives `VALIDATION_ERROR` at `ngay_ap_dung`, rather than
silently creating a fee without history. Repository implementations that do not
support atomic initial-rate creation fail before writing, instead of falling back
to two independent transactions. The legacy one-argument repository insert remains
a category-only persistence operation; the production create service always calls
the dated overload.

No new migration or backfill is needed. Deployments must already have the official
US-005 and US-006 migrations. Old default amounts are not converted into dated
history automatically: their effective dates are unknown. An authorized user must
configure a rate in US-006 using a verified effective date when needed.

## Targeted verification

- 202 unit/JavaFX tests: FeeTypeServiceTest, FeeTypeDAOTest, FeeRateDAOTest,
  FeeRateServiceTest, FeeCategoryControllerTest, FeeCategoryModuleTest,
  FeeCategoryCompletionUiTest, FeeRateControllerTest, BillingCalculatorTest,
  BillingServiceTest.
- 17 MySQL integration tests: FeeTypeMySqlIT (6), FeeRateMySqlIT (6),
  FeeRateServiceMySqlIT (4), and
  BillingMySqlIT#initialCategoryRateIsUsedByPreviewAndIssuedSnapshotSurvivesRateChanges (1).
- 1 JavaFX/MySQL integration test: InitialFeeRateUiMySqlIT. Loads the actual US-005
  and US-006 FXML/controllers/services, creates through the UI, checks missing-date
  rejection, verifies committed INITIAL history, and appends a new rate through
  the US-006 UI while preserving the first row.

All passed without failures, errors or skips. MySQL 8.4.0 used a separate local
instance on loopback port 33317 and a dedicated `bluemoon_initial_rate_test`
catalog. The failure-injection tests exercise failed parent write, failed rate
write and failure before commit after both inserts, then verify neither row exists.
The Billing test checks an initial rate effective exactly on the first day, ignores
a later-in-month rate, and verifies issued snapshots remain identical after new
backdated/future rates. The existing fee-rate tests cover date-boundary lookup,
duplicate dates and concurrent writers. No full regression suite was run.

## Branch isolation

PR #30 was open and unmerged when this branch was created from `origin/main`
`0524971`. No Billing/Household layout hotfix changes are included. No tag/release
is moved and no PR is auto-merged.
