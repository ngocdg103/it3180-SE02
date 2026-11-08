# Dev-3 independent peer review: BR-02 UI

**Review artifacts only. Do not merge this artifact branch into main.** No composite branch was pushed.
Download/fetch branch `review/br02-ui-patch`; the patch includes only 9 Dev-2 files and no BillingDAO,
BillingService, migration, Fee Rate/Billing/Notice/Payment/Debt module source or dependency commits.
PR #27 and all existing worktrees remain unchanged.

## Exact inputs

| Item | SHA |
|---|---|
| PR #27 prerequisite | `93ce0494a80fac0a431991ef4e1ea2c70fe7f5b8` |
| PR #25 prerequisite | `6339cf2e10a9090a39bab3461aa2d85f9d240a94` |
| Composite dependency base commit | `7b6364af6f5cf2cc0e4c8457fa2c5a7b1d9de506` |
| Composite dependency base tree | `973f369cd97bbafb2da8f12fe19d878ebc0995e7` |
| Consolidated commit containing ONLY BR-02 delta | `b2f54dab736a442f0413dc9fbbc62c4c6f7a3994` |
| Latest original BR-02 source HEAD | `a61d85b248cbb93496e573472f019f3710499dcf` |

The base and consolidated delta commits are local review objects, not remote refs. **The included script recreates
the exact base commit using only the two publicly available origin prerequisite commits**, then applies the patch.
Fixed synthetic metadata is used only for that local dependency base; no dependency source is embedded in the patch.
You do not need origin access to `a61d85b`, `b2f54da`, or Dev-2's worktrees. Git 2.38+ supports the required merge-tree command.

## Apply in a NEW clean worktree (PowerShell)

From your existing repository clone:

```powershell
git fetch origin
git worktree add --detach .worktrees/br02-review-artifacts origin/review/br02-ui-patch
$artifact = Join-Path (git rev-parse --show-toplevel) '.worktrees/br02-review-artifacts/docs/review/br02-ui'
& (Join-Path $artifact 'prepare-review.ps1')
```

Both target directories must be new. If either exists, choose a different artifact worktree path and/or pass
`-WorktreePath` with a new absolute path to the script. No reset/clean/discard, replacement of an existing worktree,
or main branch switch is performed. The script leaves the 9 changes staged for review in `.worktrees/dev3-br02-peer-review`.

The script checks prerequisites, recreates the exact base SHA/tree, creates a detached worktree, runs
`git apply --check`, applies with `--index`, checks whitespace and validates **every staged blob SHA** against
the latest `a61d85b` source recorded in `manifest.json`. No private/local source ref is read by the script.
If a check fails, report to Dev-2; no conflict resolution is expected from Dev-3.

For manual patch application after recreating the exact base, the equivalent commands are:

```powershell
git -C <fresh-review-worktree> rev-parse HEAD  # must be 7b6364af6f5cf2cc0e4c8457fa2c5a7b1d9de506
git -C <fresh-review-worktree> apply --check <absolute-path-to-0001-household-br02-ui.patch>
git -C <fresh-review-worktree> apply --index <absolute-path-to-0001-household-br02-ui.patch>
git -C <fresh-review-worktree> diff --cached --check
```

PR #27 is a prerequisite because it owns the two approved Billing/Notice test-fixture compatibility methods and
the five-module shell registration. They are deliberately NOT duplicated in this BR-02 patch.
Applying only this patch directly to PR #25 without #27 would omit the agreed integration context; the script
provides that context without asking you to resolve conflicts. Main merge order stays **#27 → #25 → BR-02**.

## Files in the patch

1. `docs/design/household-br02-final-shell-handoff.md`
2. `src/main/java/com/bluemoon/controllers/ApartmentHistoryPanel.java`
3. `src/main/java/com/bluemoon/controllers/HouseholdDetailController.java`
4. `src/main/java/com/bluemoon/controllers/HouseholdFormController.java`
5. `src/main/java/com/bluemoon/views/HouseholdNavigator.java`
6. `src/main/java/com/bluemoon/views/ProductionModules.java`
7. `src/main/resources/com/bluemoon/views/HouseholdDetailView.fxml`
8. `src/test/java/com/bluemoon/controllers/ApartmentHistoryPanelTest.java`
9. `src/test/java/com/bluemoon/controllers/HouseholdBr02MySqlIT.java`

## Verification and review scope

Patch SHA-256: `cdc4e5ef8ec38b5a352dcbdacf98e8726d9856ff3ed53c7c445dd7fe0aba8a2b`.
On the clean reconstructed dependency base: apply check PASS; all nine resulting source blobs match `a61d85b`
exactly; staged diff check PASS. See `manifest.json` for every target blob SHA.
The supplied prepare-review.ps1 was also run successfully in a second new worktree. An independent normal
`clean verify` on the rebuilt base + patch completed BUILD SUCCESS: **699 tests, 0 failures/errors/skipped**.

Original full-source regression: 699 normal / 700 JavaFX smoke / 832 full MySQL PASS, all zero failures/errors/skips.
Smoke/MySQL are the already reported source results, not newly rerun in this packaging task or by Dev-3. Source handoff is included.
Use JDK 17 / Maven 3.9.9 / Enforcer; the documented Windows workaround is `-Dsurefire.useManifestOnlyJar=false`.
MySQL IT requires a disposable `_test` schema and external credentials; no credentials are in this package.

Review: real BillingDAO BR-02 lookup; confirmation OK/Cancel and busy gating; read-only newest-first invoice history;
committed Payment fields without recomputation; ADMIN/ACCOUNTANT view, RESIDENT denial; off-FX service calls,
retry/error, dispose and listener cleanup. No production owner contract was changed.
