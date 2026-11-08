# US-011 Fee notices — handoff (Dev-3)

- Branch: `feature/us011-notice`, PR base `feature/us007-billing-ui` (stacked on US-007 Billing UI, itself on PR #14). Retarget as the lower PRs merge.
- Source of data: stored US-007 invoices only, read through `BillingService.getPeriod/getInvoice`. Nothing is recomputed and nothing is stored (no new table, no migration).

## Files

- `models/NoticeSlip`, `services/NoticeService`, `services/NoticeServiceImpl`, `services/NoticeFormatter`.
- `controllers/NoticeController`, `controllers/NoticeModule`, `views/NoticeView.fxml`.
- Tests: `NoticeServiceTest`, `NoticeFormatterTest`, `NoticeControllerTest`.

## Acceptance criteria (EP-04 US-011)

| AC / BR | Result |
|---|---|
| AC-01 view P1204 notice, total equals invoice 100% | Done. Tested end to end (real BillingServiceImpl): title, apartment, head, area, people, each fee line `80 m² x 10.000 = 800.000 VNĐ`, voluntary `0 VNĐ`, total `1.384.000 VNĐ`, due date, transfer text `P1204 Dong phi thang 11/2026`. Lines/total are the invoice snapshot objects. |
| AC-02 refuse when period not generated | Done: "Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!" |
| BR-16 only after US-007 | Done by construction: notices are built from stored invoices. |
| BR-17 preview on screen | Done (text preview). |
| BR-17 batch export | Partly: whole-period export as a printable HTML file (one page per notice, `page-break`), per notice or all. **No PDF** (no PDF library in the project); use the browser's Print / Save as PDF. |
| `ma_thong_bao` `TB-[Kỳ]-[SốCănHộ]` | Done (derived, not stored). |
| `ngay_phat_hanh` default today | Done: current date in Asia/Ho_Chi_Minh. |
| `han_nop_tien` | The invoice's `hanNop` (15th of the next month, from US-007). |
| `noi_dung_chuyen_khoan` | Done: `[SoPhong] Dong phi thang [MM/YYYY]`. |
| `thong_tin_ngan_hang` | **Open decision for PO.** No bank account is defined anywhere in the SRS data or config, so nothing is hard-coded. The user types it on the screen; export is refused until it is filled; the on-screen preview shows a visible placeholder. |

## Decisions / limits

- Permission: `DEBT_REPORT_VIEW` (ADMIN, ACCOUNTANT). The RBAC matrix has no separate notice permission and `Permission` was not changed. PO to confirm.
- "Phát hành" is modelled as view + export. Nothing is persisted and no SMS/e-mail is sent (not required by the SRS).
- Head name comes from `ResidentDAO.findHead`; "(chưa có chủ hộ)" if none.
- Loading a period reads every invoice (one `getInvoice` per household): fine for a building-sized dataset.
- HTML export escapes all dynamic text. The file chooser uses the host window found from the module's view; the module creates no Stage/Scene.
- Printing through the system printer dialog is not implemented.

## Registration (App Shell owner; not edited here)

`ProductionModules.create(...)`: `NoticeModule.registration(connections)` → `ModuleRegistration("notices", "Thông báo thu phí", Permission.DEBT_REPORT_VIEW, ...)`.

## QA E2E (needs the registration, US-007 published for the month, MySQL test schema)

1. Publish 2026-11 as in the Billing UI handoff. Open "Thông báo thu phí".
2. Enter period `2026-11` (or pick it), click "Tải hóa đơn của kỳ": apartments are listed; select P1204: the notice appears with total 1.384.000.
3. Period `2026-12`: message "Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!".
4. Leave the bank box empty and click an export button: validation message, no file. Fill it, export one / all, open the HTML in a browser and print.
5. RESIDENT: the screen is empty and disabled.
