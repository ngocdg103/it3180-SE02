# US-001 UI — Dev-2 handoff

Scope: WBS 1.1.1.2.2, Sprint 1 / Iteration 02. Base `cd49be571248330f5a454de6e31a3e386f7bf8ec`, branch `feature/us001-household-ui`.

## Composition contract

The module is intentionally not wired into BlueMoonApplication: this base has Foundation and Auth backend but no approved Login UI integration. No authentication or RBAC bypass is added. A future authenticated application composition root supplies one existing HouseholdService instance and a view host:

```java
// Run on the JavaFX Application Thread, after real login/session setup.
var navigator = new HouseholdNavigator(householdService, root -> container.setCenter(root));
navigator.showList();
// On module exit or logout, before clearing/replacing the host:
navigator.dispose();
```

HouseholdServiceImpl still checks SessionContext: ADMIN can read/write, ACCOUNTANT can read, others are denied. Controllers catch AccessDeniedException and clear displayed profile/form data. No new role policy is implemented. Fake services, an ADMIN role supplier and InMemoryHouseholdRepository appear only in test code.

## Views and behavior

- HouseholdListView: six profile columns, apartment status filter, reload/create/detail, progress, empty/error and retry states. A successful create/update returns to an unfiltered, refreshed list with the service success message.
- HouseholdDetailView: loads by maHo, shows six fields, edit/back. No member/household-head fields exist in this contract.
- HouseholdFormView: create/edit, backend field errors, raw text inputs, ISO DatePicker and enum ComboBox. Update identifiers are read-only. Failed validation retains inputs. Save is disabled while the request is active.
- DUPLICATE shows a warning dialog; CONFIRMATION_REQUIRED shows confirmation. Approval repeats update with the same immutable DTO and confirmAreaChange=true; cancel does not issue a second update.
- Service calls execute in daemon JavaFX Tasks. Controls and navigation update on the FX thread. Disposing/replacing views clears old data and ignores late callbacks; it does not pretend an already submitted database write can be rolled back by cancelling the view.

Call showDetail(maHo) for a fresh service read. showEdit(profile) accepts a profile previously read through the authorized service. The host remains responsible for ending the module lifecycle on logout and preventing access to stale authenticated application screens.

## Approved limits and dependencies

- BR-02: ApartmentChargeLookup.NONE remains unchanged. UI handles CONFIRMATION_REQUIRED and tests it with a fake; no claim that real charge history is connected.
- BR-03: API has no member count; UI does not invent a zero count.
- Existing 1:1 database relationship and immutable identifiers are retained. No extra apartment/floor consistency rule is added.
- Existing Dev-3 Service/DAO/models/API and migration/schema/seed are unchanged.
- MySQL must have the existing US-001 migration applied by the responsible owner. End-to-end acceptance requires real Login, an appropriate session and MySQL integration.
- pom.xml only isolates Surefire test-class JVMs (reuseForks=false), avoiding conflicts with FoundationSmokeIT's JavaFX startup/exit. Tests use the software Prism renderer (prism.order=sw) after a native JavaFX crash during snapshot verification. Production rendering settings are unchanged. No dependencies or framework changes.
