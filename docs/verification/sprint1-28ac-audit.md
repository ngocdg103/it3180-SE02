# Audit 28 Acceptance Criteria Sprint 1

Ngày audit: **26/11/2026**, múi giờ Asia/Saigon. Phạm vi: đúng 28 dòng PO cung cấp trong cuộc hội thoại, thuộc 11 US. Đây là audit, không phải PR sửa chức năng hoặc thay đổi AC.

## 1. Baseline và giới hạn kết luận

| Thành phần | Trạng thái / SHA |
|---|---|
| `origin/main` sau fetch | `c8df66e0829fde17f290c1269b82365ed60e23b2` |
| [PR #30](https://github.com/ngocdg103/it3180-SE02/pull/30) | OPEN, chưa merge; head `1aca7eafbaa87d5a4889d5f6bb260f40dd2143b2` |
| [PR #31](https://github.com/ngocdg103/it3180-SE02/pull/31) | OPEN, chưa merge; head `576663f99d3b355d4ef6c0e65cb4ab8d45e80685` |
| **Commit thực sự được audit** | **`eb369f738cb413c7e0a778f98fd613b449771354`** |
| Tree của baseline tích hợp | `80b85b96e64971289e5743c5afe81f641e44bebd` |
| Parents của baseline, theo thứ tự | main, head #30, head #31 như trên |
| Worktree kiểm tra | `.tools/audit-28ac`, detached HEAD; merge octopus tạm, không thay đổi main |
| Branch báo cáo | `docs/sprint1-28ac-audit`, tạo riêng từ main; không chứa merge code hai hotfix |
| Tag `v0.1.0-sprint1` | Giữ nguyên `416c3aea3f39a909c3b2e999369b95372e83f477` |

Các kết quả dưới đây chỉ áp dụng cho **baseline tích hợp**, không phải chứng nhận main hiện tại đã có hai hotfix. Không tái sử dụng các con số 699/700/832 từ tài liệu cũ để kết luận PASS. Chẳng hạn `docs/test/README.md` ghi SHA `89b3a28`, còn `docs/SPRINT1_REPORT.md` ghi `aff01b6`; cả hai khác baseline này.

Môi trường: Temurin JDK 17.0.17, Maven 3.9.9, JavaFX 17.0.18, MySQL Community 8.4.0 trên loopback:33317. Dùng riêng `bluemoon_audit28_test` và `bluemoon_audit28_probe_test`, không dùng DB demo/production. Các ca UI gọi FXML/controller/service thật khi ghi rõ MySQL; các ca unit dùng fixture được ghi riêng. Không kiểm tra Windows DPI 125%/150% trong audit này và không lấy FXML load/build thành bằng chứng rằng mọi layout đều đạt.

## 2. Nguồn yêu cầu và cách đối chiếu

Nguồn chính là [SRS](../srs/README.md): [EP01](../srs/EP01_QuanLyCuDan.md), [EP03](../srs/EP03_ThuPhi.md), [EP04](../srs/EP04_ThongBao.md), [EP06](../srs/EP06_VanHanhHeThong.md), [EP07](../srs/EP07_TraCuuThongKe.md). Đã đối chiếu [Implementation WBS](../../SE_02_Implementation_WBS.xlsx), README, USER_GUIDE, developer-setup, SPRINT1_REPORT, KNOWN_LIMITATIONS, tài liệu test và các handoff liên quan.

**Cột “Nguyên văn AC” giữ nguyên 28 câu trong bảng PO do người giao việc cung cấp ngày 26/11/2026.** SRS trong repo chỉ có **24 AC được đánh số** và không dùng hoàn toàn cùng số thứ tự. Không sửa hoặc tự đánh lại số của SRS. Các bảng nguồn `SE_02-W02-UserStory.xlsx` được WBS nhắc đến không có trong baseline; bảng PO trong hội thoại là nguồn trực tiếp cho 28 câu dưới đây.

Các ánh xạ quan trọng:

- PO US-001 AC2 tương ứng SRS US-001 AC-03; PO AC3 tổng hợp validation mục 1.1 và ràng buộc duy nhất, không phải SRS AC-03.
- PO US-002 AC2 rộng hơn SRS AC-02 (CCCD người từ 14 tuổi), được đối chiếu thêm mục 2.1 và WBS `02 THEO USER STORY!J6`.
- PO US-005 AC2 dựa BR-07 và WBS contract `03 HOP DONG TICH HOP!B9`; AC3 dựa mục 1.1, không có AC-03 riêng trong SRS.
- PO US-006 AC1/AC2 cùng đối chiếu SRS AC-01 và BR-09; AC3 dựa mục 2.1.
- PO US-007 AC2 đối chiếu BR-11 và quyết định Q7 trong handoff; SRS AC-02 thực tế là chống lập trùng kỳ, không phải thiếu dữ liệu.
- PO US-008 AC1 dựa mục 4.1 và AC-01; AC2 dựa AC-01/02, BR-12/13; AC3 dựa validation mục 4.1.
- PO US-009 AC1/AC2 dựa mục 5.1, BR-12/15; AC3 còn có WBS `03 HOP DONG TICH HOP!E13`. SRS chỉ có một AC lọc danh sách nợ.
- PO US-014 AC1 gộp tìm phòng, tên, chi tiết/phân quyền và bổ sung “số hộ khẩu”; PO AC2 tương ứng SRS AC-03, không phải SRS AC-02 tìm tên.
- PO US-018 AC1 dựa ma trận EP06 và AC-01; AC2 gộp AC-02, BR-21/22 và yêu cầu không đổi DB.

WBS có nhãn thiết kế “ĐỀ XUẤT” tại `00 HUONG DAN!B13`; không coi tên class trong WBS hoặc trạng thái `Not Started` cũ là chứng cứ triển khai. Các quyết định đã chốt tại sheet `04 QUYET DINH CHUNG`, đặc biệt DEC-06/07/08/09 và DEC-14, được dùng cùng SRS. README/hướng dẫn không có quyền ghi đè chúng.

## 3. Bằng chứng chạy trên baseline

| Mã | Phạm vi | Đã chạy | PASS | FAIL | ERROR / SKIP |
|---|---|---:|---:|---:|---:|
| U | Các method unit/service/JavaFX được chọn theo 28 AC | 195 | 195 | 0 | 0 / 0 |
| M | Các method MySQL và JavaFX/MySQL của baseline | 25 | 24 | 1 | 0 / 0 |
| P | Probe audit bổ sung, MySQL thật + UI thật + fingerprint DB | 8 | 8 | 0 | 0 / 0 |
| **Tổng** | **Không phải full regression** | **228** | **227** | **1** | **0 / 0** |

Chi tiết tên ca, selector, kết quả và SHA ở [manifest bằng chứng](sprint1-28ac-audit-evidence.json). Probe được lưu nguyên nguồn tại [Sprint1AuditProbeTest.java](probes/Sprint1AuditProbeTest.java). Nó chỉ được đưa tạm vào thư mục test của worktree để chạy, không sửa source production hoặc test tracked của baseline. Tám ca probe chạy theo ba lượt 6 + 1 + 1; hai ca bổ sung không đổi nội dung sáu ca trước.

**Test thất bại được giữ nguyên:** `AuthMySqlIT.schemaAndSeedCanBeReappliedAndSrsAdminCanLogin` dừng ở dòng 68: expected `Admin`, actual `Ban Quản Trị Hệ Thống`. Assertion đăng nhập thành công ngay trước đó đã qua. Seed mới trong `database/seed.sql` đặt tên hiển thị khác ví dụ SRS/test cũ. Probe đăng nhập bằng seed hiện tại và mở Dashboard thật đã xác minh role ADMIN và lời chào lấy đúng tên lưu trong DB. Không sửa seed/test để làm xanh lần audit; US-012 AC1 được giữ **REQUIREMENT AMBIGUITY** chờ quyết định về tên mẫu/literal, thay vì coi chức năng đăng nhập bị hỏng hoặc bỏ qua test đỏ.

Fingerprint probe đọc toàn bộ hàng của 11 bảng: `users`, `apartments`, `households`, `residents`, `fee_types`, `fee_rates`, `billing_periods`, `invoices`, `invoice_lines`, `phieu_thu`, `apartment_credit`; sắp thứ tự, băm SHA-256 rồi so trước/sau. Trong lượt probe sáu ca:

- Thiếu dữ liệu: trước = sau = `704d9218320f121ec6e5b68a314b52d2b776591323c2841335dd54c90c01a537`.
- Service trái quyền: trước = sau = `94426478c1c6d06812e635643ca5803b8565fc5de2c85c355fb5d69ddf4c12d4`.

Đây là bằng chứng trên fixture cô lập, không phải checksum của DB demo. Hai hash khác nhau vì mỗi ca có fixture/thời điểm tạo riêng. Việc so hash chứng minh trạng thái hàng đã commit không đổi; không tuyên bố kiểm tra toàn bộ tác dụng phụ bên ngoài DB hay sequence AUTO_INCREMENT.

## 4. Ma trận 28 AC

Code links được ghim vào SHA nguồn tương ứng của main/#30/#31; các file đó giữ cùng nội dung trong tree tích hợp. U/M/P dẫn đến lượt kiểm thử ở trên; mỗi dòng còn nêu ca và kết quả thực tế, không chỉ sự tồn tại của class hoặc nút.

| US/AC | Nguyên văn AC | Code liên quan | Test liên quan | Kết quả | Bằng chứng | Việc cần làm |
|---|---|---|---|---|---|---|
| US-001 AC1 | Tạo hồ sơ hộ gia đình và liên kết đúng với căn hộ | [HouseholdServiceImpl] `createHousehold`; [HouseholdDAO] `insert`; [HouseholdFormController] | U: `HouseholdServiceTest.createsValidHouseholdAndLinksItToItsApartment`; M: `HouseholdMySqlIT.serviceOnRealDatabaseCoversAcceptanceCriteria`; P: `actualHouseholdUiCreateAndInvalidFields` | PASS | UI thật tạo `AUDIT_NEW` liên kết `P2201`, đọc lại diện tích `85.00`, hàng xuất hiện trên bảng. Hai bản ghi hộ/căn hộ được ghi qua transaction. | Không cần hotfix AC này. |
| US-001 AC2 | Cập nhật hồ sơ hộ gia đình và hiển thị thông tin mới | [HouseholdServiceImpl] `updateHousehold`; [HouseholdDAO] `update`; [HouseholdFormController] | U: `updatesStatusAndLeavesOtherDataUntouchedAc03`, `detailLoadsAndEditAc03KeepsIdentifiersReadOnly`; M: `HouseholdBr02MySqlIT.productionHouseholdConfirmationCancelApprovalAndHistoryPreserveSnapshot` | PASS | Hủy xác nhận không ghi; chấp thuận sửa diện tích lưu giá trị mới, refresh detail; hóa đơn/lịch sử trước đó giữ nguyên. | Không cần hotfix; merge #30 theo review riêng để có layout đã tích hợp trong baseline audit. |
| US-001 AC3 | Không lưu hồ sơ khi dữ liệu thiếu hoặc không hợp lệ | [HouseholdServiceImpl] `validateEditable`; [HouseholdFormController] `handle` | U: `everyMissingFieldIsReportedTogether`, `rejectsNonPositiveOrMalformedArea`, `validationKeepsRawInputAndDisplaysAllBackendFieldErrors`; P: `missingFieldsAreRejectedWithoutDatabaseMutation`, `actualHouseholdUiCreateAndInvalidFields` | PASS | Bỏ trống form và diện tích 0 bị chặn, UI có lỗi trường, fingerprint 11 bảng không đổi. Các biên/sai định dạng chạy ở U. | Không cần hotfix. |
| US-002 AC1 | Thêm nhân khẩu hợp lệ vào đúng hộ gia đình | [ResidentServiceImpl] `createForHousehold`; [ResidentDAO]; [ResidentPanelController] | U: `ac01AddsFirstHeadAndHouseholdShowsHimWithCount`, `createRefreshesListAndDuplicateClickDoesNotInsertTwice`; M: `ResidentMySqlIT.serviceOnRealDatabaseCoversTheAcceptanceCriteriaRbacAndDatabaseErrors` | PASS | Nhân khẩu đúng FK hộ, đọc lại được; UI refresh danh sách và số thành viên. Ca nhấp lặp chỉ tạo một người. | Không cần hotfix. |
| US-002 AC2 | Không tạo nhân khẩu khi chưa chọn hộ hoặc thiếu trường bắt buộc | [ResidentServiceImpl] validation `ma_ho`, tên/ngày/enum/CCCD; [ResidentPanelController] `create` | U: `missingHouseholdChoiceIsAValidationError`, `allErrorsAreReportedTogether`, `adultWithoutCccdRetainsInputAndDisplaysFieldError`; P: `missingFieldsAreRejectedWithoutDatabaseMutation`, `actualResidentUiReportsMissingRequiredFieldsWithoutCreatingPerson` | PASS | Service chặn `maHo` rỗng; form nhân khẩu thiếu tên/ngày sinh/giới tính/quan hệ/ngày chuyển đến hiện lỗi; không ghi DB. UI hiện tại đã gắn hộ theo panel, không có ô chọn hộ độc lập; không dùng điều đó để bỏ kiểm tra Service. | Không cần hotfix. |
| US-005 AC1 | Tạo khoản thu và phân loại bắt buộc/tự nguyện | [FeeTypeServiceImpl] `save`; [FeeTypeDAO]; [FeeCategoryController] `onKindChanged` | U: `lifecycleAndBothAcceptanceCriteria`, `contributionForcesVoluntaryMethodAndZeroAndLocksFields`; M: `FeeTypeMySqlIT.lifecycleDuplicateFilterAndNoHardDelete`, `InitialFeeRateUiMySqlIT` | PASS | Bắt buộc chọn phương thức hợp lệ; tự nguyện ép `TU_NGUYEN`/0. Với #31, giá dương + ngày chọn tạo một INITIAL thật; US-006 đọc được, không phải dòng UI giả. | Không cần sửa thêm; kết quả bao gồm #31 chưa merge. |
| US-005 AC2 | Chỉnh sửa hoặc ngừng áp dụng khoản thu | [FeeTypeServiceImpl] `updateFeeType/deactivateFeeType`; [FeeTypeDAO]; [FeeCategoryController] | U: `FeeCategoryCompletionUiTest.editsAllowedFieldsKeepsCodeImmutableAndRefreshes`, `deactivateRequiresConfirmationCancelDoesNothingThenRefreshes`; M: `FeeTypeMySqlIT.lifecycleDuplicateFilterAndNoHardDelete` | PASS | UI sửa và refresh, mã không đổi; Cancel không ghi, xác nhận chuyển NGUNG_AP_DUNG; DB vẫn giữ hàng. | Không cần hotfix. Giá hiệu lực mới tiếp tục thêm qua US-006. |
| US-005 AC3 | Không lưu khoản thu khi thiếu tên hoặc phân loại | [FeeTypeServiceImpl] `save`; [FeeCategoryController] `validate/showFieldErrors`; `ck_fee_name/ck_fee_kind` | U: `nullFormReportsAllFieldsTogether`, `validatesNameBoundariesAndEnums`, `tooShortOrTooLongNameIsRejected`; P: `actualFeeAndRateUiShowMissingFields`, `missingFieldsAreRejectedWithoutDatabaseMutation` | PASS | Đã bỏ trống tên riêng và phân loại riêng trên UI thật; có `nameError/kindError`, không thêm khoản thu hay INITIAL; hash DB không đổi. | Không cần hotfix. |
| US-006 AC1 | Lưu đơn giá và ngày bắt đầu hiệu lực | [FeeRateServiceImpl] `createRate`; [FeeRateDAO] `insert`; [FeeTypeDAO] dated insert | U: `creationIsAppendOnlyAndKeepsExactDecimalDateAndUnicode`; M: `FeeRateMySqlIT.effectiveHistoryBackdateAndExactDecimals`, `InitialFeeRateUiMySqlIT` | PASS | DB giữ đúng số tiền/ngày và history có id thật; thêm giá mới không ghi đè hàng cũ. INITIAL của #31 dùng ngày chọn, không tự suy ngày. | Không cần hotfix. |
| US-006 AC2 | Áp dụng đúng đơn giá có hiệu lực tại kỳ thu | [FeeRateDAO] `findEffectiveRate`; [BillingDAO] `loadInputs` | M: `effectiveHistoryBackdateAndExactDecimals`, `BillingMySqlIT.rateInForceAtFirstDayOfPeriodIsUsed` | PASS | Chọn bản ghi có ngày lớn nhất ≤ ngày đầu kỳ; giá tương lai không áp ngược cho kỳ cũ. SQL của Billing đọc cùng connection transaction. | Không cần hotfix. |
| US-006 AC3 | Yêu cầu bổ sung khi thiếu đơn giá hoặc ngày áp dụng | [FeeRateServiceImpl] validation; [FeeRateController] `save` | U: `invalidPricesAreFieldErrorsBeforeIo`, `nullFormInvalidCodeDateAndNoteAreReportedTogether`, `invalidPriceDateAndLongNoteReachFieldErrorsWithoutInsert`; P: `actualFeeAndRateUiShowMissingFields` | PASS | Giá rỗng với ngày hợp lệ và ngày rỗng với giá hợp lệ đều hiện lỗi đúng trường trên UI; lịch sử và hash DB không đổi. | Không cần hotfix. |
| US-007 AC1 | Lập khoản phải thu, tính phí dịch vụ và phí quản lý theo diện tích, đơn giá | [BillingCalculator] `quantity/compute`; [BillingDAO] `loadInputs`; [BillingController] | U: `BillingCalculatorTest.ac01P1204`, `previewShowsServiceNumbersLinesAndWritesNothing`; M: `BillingMySqlIT.ac01EndToEndOnRealMySql` | PASS | Fixture MySQL thực lưu PHI_DV và PHI_QL là THEO_DIEN_TICH, PHI_VS là THEO_NHAN_KHAU. 80m²/4 người cho DV 800.000, QL 560.000, VS 24.000; tổng 1.384.000. README/USER_GUIDE sai mô tả, không phải bằng chứng công thức khác. | P1 sửa tài liệu bằng PR khác; kiểm tra cấu hình DB demo trước trình diễn. Không đổi công thức. |
| US-007 AC2 | Chỉ ra dữ liệu còn thiếu, không tạo kết quả tính phí sai | [BillingCalculator]; [BillingServiceImpl]; [BillingController] `issues` | U: `missingOrInvalidAreaSkipsOnlyThatHousehold`, `missingRateAbortsWholePeriodAndListsCodes`, `missingRateBlocksPublishingAndNamesTheFee`, `zeroTotalAndWarningsAreListedAndSkippedHouseholdsAreNotBilled`; M: `missingRateRollsBackEverythingAndReleasesTheKey`, `missingRateIsShownAndNothingIsWrittenOnRealMySql` | PASS | Thiếu giá: báo mã khoản thu, chặn phát hành và không có kỳ/hóa đơn; thiếu diện tích: bỏ riêng hộ và có lý do trong issues theo BR-11/Q7. UI thật có thông báo thiếu giá. | Không cần hotfix nghiệp vụ. Missing-area dùng fixture calculator vì schema hợp lệ đã chặn diện tích rỗng/≤0. |
| US-008 AC1 | Lưu đúng hộ/người nộp, khoản thu, số tiền và ngày nộp | [ReceiptServiceImpl] `recordPayment`; [ReceiptDAO] `record`; [ReceiptController] | U: `successRefreshesReceiptsAndCreatesNewIntentWithoutFxJdbc`; M: `ReceiptMySqlIT.partialThenFullAc01Ac02AndSnapshotNeverChanges`; P: `receiptRoundTripKeepsPayerInvoiceHouseholdFeesDateAndAmount` | PASS | Đọc lại đúng người nộp, amount 100.00, ngày 08/11/2026, chuyển khoản, ghi chú, request id, created_by=71. JOIN phiếu→invoice→invoice_lines xác nhận đúng AUDIT_HH/P1204/AUDIT_FEE. | Không cần hotfix. “Khoản thu” được truy vết qua hóa đơn theo SRS 4.1, không giả định phiếu phải phân bổ riêng từng dòng phí. |
| US-008 AC2 | Cập nhật tình trạng đóng phí sau khi ghi nhận thanh toán | [ReceiptDAO]; [DebtDAO]; [ReceiptController] | M: `partialThenFullAc01Ac02AndSnapshotNeverChanges`, `overpaymentPersistsCreditAtomically`, `DebtMySqlIT.partialAndFullPaymentsUpdateDebtAndWholePeriodKpis` | PASS | Nộp 1.000.000: còn 384.000/DONG_THIEU; nộp tiếp 384.000: còn 0/DA_DONG_DU; nộp thừa: DONG_THUA và credit; khoản phải thu/snapshot dòng không đổi. | Không cần hotfix trạng thái; tự cấn trừ credit kỳ sau vẫn là hạn chế đã ghi riêng. |
| US-008 AC3 | Yêu cầu bổ sung nếu thiếu khoản thu, số tiền hoặc ngày nộp | [ReceiptServiceImpl] validation; [ReceiptController] `controls/save/show` | U: `invalidFieldsAndUnicodeAreValidatedTogether`, `invalidAmountNeverCallsRepository`; P: `missingFieldsAreRejectedWithoutDatabaseMutation`, `actualReceiptUiShowsMissingAmountAndDate` | PASS | Service chặn từng trường hóa đơn/amount/ngày bị bỏ trống; UI hiện lỗi amount/ngày; không chọn hóa đơn thì nút Lưu disabled. Hash DB không đổi, không sinh phiếu hay cập nhật tiền đã nộp. | Không cần hotfix. |
| US-009 AC1 | Hiển thị đúng số phải thu, đã thu, còn thiếu và trạng thái | [DebtServiceImpl] `getDebtByPeriod`; [DebtDAO]; [DebtController] | U: `initialLatestAndOutstandingFloorFiltersShowWholePeriodKpisAndFilteredFooter`; M: `partialAndFullPaymentsUpdateDebtAndWholePeriodKpis`, `floorAndStatusFilterEmptyPeriodAndHouseholdBalance` | PASS | KPI toàn kỳ và footer sau lọc được kiểm độc lập; chỉ hiển thị đúng trạng thái và số nợ. Nộp thừa không bù âm vào nợ hộ khác. | Không cần hotfix. |
| US-009 AC2 | Cập nhật công nợ tương ứng với giao dịch thanh toán | [ReceiptDAO] transaction; [DebtDAO] read; [DebtServiceImpl] | M: `DebtMySqlIT.partialAndFullPaymentsUpdateDebtAndWholePeriodKpis`; `ReceiptMySqlIT.partialThenFullAc01Ac02AndSnapshotNeverChanges` | PASS | Đọc báo cáo lại sau từng commit cho số đã thu/còn thiếu/trạng thái tương ứng thanh toán; không lấy số mock làm chứng cứ DB. | Không cần hotfix. UI cần tải/lọc lại để đọc giao dịch mới; không có yêu cầu push realtime trong AC này. |
| US-009 AC3 | Thông báo khi kỳ thu chưa có dữ liệu | [DebtServiceImpl] `noData`; [DebtController] | U: `invalidFloorAndPeriodDoNotQueryAndNoDataClearsOldKpis`; M: `DebtMySqlIT.floorAndStatusFilterEmptyPeriodAndHouseholdBalance` | PASS | Kỳ chưa có dữ liệu trả NO_DATA, UI báo “Không có dữ liệu phù hợp.”, bảng rỗng/KPI xóa. Phân biệt kỳ rỗng với kỳ có dữ liệu nhưng filter không khớp. | Không cần hotfix. |
| US-011 AC1 | Tạo thông báo đúng hộ, khoản thu, số tiền và kỳ hạn | [NoticeServiceImpl] `slip`; [NoticeController]; [NoticeFormatter] | U: `ac01ShowsTheFullNoticeThatMatchesTheInvoice`, `exportNeedsBankInfoThenWritesEscapedPrintableHtml`; M: `noticeFromTheInvoicePublishedByTheBillingModuleAndExport` | PASS | Preview/HTML lấy snapshot hóa đơn thật, đúng căn hộ, các dòng phí, tổng 1.384.000 và hạn 15/12/2026; đầu ra in ấn không tự tính lại. | Không cần hotfix. Xuất là Printable HTML; không tuyên bố có native PDF. |
| US-011 AC2 | Không phát hành thông báo nếu chưa có danh sách khoản phải thu | [NoticeServiceImpl] `getNotices/translate`; [NoticeController] `updateControls/export` | U: `periodNotGeneratedIsRefusedWithTheSrsMessage`; M: `noticeRefusesAPeriodThatWasNeverGenerated` | PASS | MySQL không có kỳ: từ chối với thông báo đúng SRS “Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!”, không có danh sách để xuất; nút export disabled. | Không cần hotfix. |
| US-012 AC1 | Cho phép truy cập khi thông tin đăng nhập đúng | [AuthService] `login`; [LoginController]; [DashboardController] `initialize`; [Seed]; [AuthMySqlIT] | U: `validLoginPublishesSafeGlobalSessionAndLogoutClearsIt`, `successAndLogoutClearSessionAndFields`; M: `schemaAndSeedCanBeReappliedAndSrsAdminCanLogin` **FAIL**; P: `seededAdminAuthenticatesAndDashboardDisplaysStoredName` **PASS** | REQUIREMENT AMBIGUITY | Đăng nhập thật thành công/role ADMIN/Dashboard hoạt động. Nhưng SRS EP06 AC-01 và test yêu cầu lời chào Admin; seed hiện là Ban Quản Trị Hệ Thống, Dashboard dùng tên lưu. Chưa tìm thấy quyết định sửa chính câu kỳ vọng này. | P1 Lead/PO xác nhận tên là dữ liệu mẫu hay literal bắt buộc; sau đó sửa đúng test/seed/tài liệu trong PR khác. Không đổi xử lý xác thực trong audit. |
| US-012 AC2 | Từ chối truy cập và thông báo khi thông tin đăng nhập sai | [AuthService] `login`; [LoginController] `INVALID_CREDENTIALS` | U: `wrongPasswordAndUnknownAccountHaveSameErrorAndNoIdentity`, `invalidCredentialsShowsOwnedWarningAndStaysOnLoginAfterDismissal`; M: `jdbcLoginLockPersistsAcrossServicesAndExpiresAtDeadline` | PASS | Sai thông tin không vào Dashboard, cảnh báo chung không lộ tài khoản; DB đếm sai/khóa đúng ngưỡng SRS “quá 5 lần”, hết hạn 5 phút. Việc tăng counter là tác động DB hợp lệ của đăng nhập sai. | Không cần hotfix. |
| US-012 AC3 | Yêu cầu bổ sung khi bỏ trống thông tin đăng nhập | [AuthService] validation đầu hàm; [LoginController] `login` | U: `fxmlAndMissingFields`, `missingAndMalformedInputsNeverQueryDatabase`; P: `missingFieldsAreRejectedWithoutDatabaseMutation` | PASS | Cả username rỗng, password rỗng, cả hai rỗng đều báo đúng thông điệp; UI không gọi auth; gọi Service thật cũng không đổi 11 bảng và không có session mới. | Không cần hotfix. |
| US-014 AC1 | Tìm kiếm theo tên, số căn hộ hoặc số hộ khẩu; xem chi tiết theo quyền | [ResidentSearchCriteria]; [ResidentSearchServiceImpl]; [ResidentDAO] `search`; [ResidentSearchController]; [HouseholdNavigator] | M: `apartmentPrefixAndEmptyApartmentIncludeHouseholdAndHead`, `accentsCaseCccdFloorAndWildcardLiterals`, `realUiEnterSearchEmptyMessageAndDetailNavigation`, `deniedRoleCannotQueryEvenWithValidInput`; rà enum/query/UI trực tiếp | FAIL | **CONFIRMED GAP đối với bảng PO:** có tên/phòng/CCCD/tầng và mở detail đúng guard, nhưng không có criterion, query hay UI cho số hộ khẩu. Test đang có không chứng minh khả năng đó. `maHo` trong kết quả chỉ dùng điều hướng, không phải bằng chứng tìm theo số hộ khẩu. | P1 PO định nghĩa số hộ khẩu, quan hệ với `ma_ho`, ví dụ và cơ chế khớp; Dev mới thiết kế hotfix dữ liệu/query/UI/tests. Không tự đồng nhất hai khái niệm. |
| US-014 AC2 | Thông báo khi không tìm thấy hồ sơ phù hợp | [ResidentSearchServiceImpl] `search`; [ResidentSearchController] | M: `realUiEnterSearchEmptyMessageAndDetailNavigation` | PASS | P9999 cho bảng rỗng và đúng thông điệp SRS “Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!”, không lỗi hệ thống; dữ liệu tìm kiếm là chỉ đọc. | Không cần hotfix. |
| US-018 AC1 | Cho phép sử dụng chức năng tương ứng với quyền được cấp | [RoleBasedAuthorizationService]; [DashboardController]; [ProductionModules] | U: `allPermissionsAcrossAllRoles`, `missingSessionDeniesEveryPermissionWithoutCallingResolver`, `registrationsUseRequiredPermissionsAndUniqueIds`; M/P: các luồng ADMIN/ACCOUNTANT tạo hộ/phí/thu tiền thực tế ở trên | PASS | Ma trận 22 permission × 3 role và null-session được kiểm; ADMIN tạo hộ/phí nhưng không thu tiền, ACCOUNTANT thu tiền và xem báo cáo, các module mở theo guard. | PASS cho các module Sprint 1 được triển khai. Nhánh RESIDENT-own vẫn là dependency được handoff ghi PO defer; không tuyên bố production đã có cổng hồ sơ/lịch sử riêng cho cư dân. |
| US-018 AC2 | Từ chối truy cập vượt quyền và đảm bảo dữ liệu không bị thay đổi | Guard đầu Service; [RoleBasedAuthorizationService]; [ReceiptController] `controls`; [DashboardController] `openModule` | U: `everyEntryPointUsesApprovedPermissionAndStopsDaoWhenDenied`, `deniedUpdateDoesNotAlterExistingData`, `adminCannotRecordAndResidentCannotLoad`; P: `deniedServiceCallsLeaveAllTablesUnchanged` | PASS | Gọi trực tiếp Service với payload hợp lệ: ACCOUNTANT tạo hộ/nhân khẩu, ADMIN thu tiền, RESIDENT tạo/ngừng phí, đặt giá, lập/đọc hóa đơn, đọc nợ/thông báo/search đều bị AccessDeniedException. Fingerprint 11 bảng trước/sau giống hệt; UI ẩn/disable chức năng sai quyền. | Không cần hotfix cho các guard đã kiểm. Đây là bảo vệ tầng Service, không khẳng định DAO tự có RBAC. |

## 5. Kết luận riêng hai AC đỏ

### US-007 AC1: PASS cho công thức; có lỗi tài liệu xác nhận được

Nguyên văn SRS EP03 §3.2:

> Tiền dịch vụ = Diện tích_A × Đơn giá DV.
> Tiền quản lý = Diện tích_A × Đơn giá QL.

Nguyên văn nội dung SRS §3.3 AC-01:

> **Given:** Căn hộ `P1204` diện tích $80m^2$, có 4 nhân khẩu. Đơn giá: QL 7.000đ, DV 10.000đ, VS 6.000đ.
>
> **When:** Kế toán bấm "Lập kỳ thu tháng 2026-11".
>
> **Then:** Hóa đơn căn hộ `P1204` có: DV = 800.000đ, QL = 560.000đ, VS = 24.000đ → Tổng tiền = **1.384.000 VNĐ**, trạng thái `CHUA_DONG`.

EP01 BR-02 cũng nói diện tích làm căn cứ cho cả quản lý và dịch vụ. WBS `04 QUYET DINH CHUNG!H12` (DEC-08, Đã chốt): “QL 7.000đ/m2, DV 10.000đ/m2, Vệ sinh 6.000đ/người”. Không tìm thấy quyết định PO trong nguồn đã đọc thay phí dịch vụ thành theo nhân khẩu.

Calculator không đoán công thức theo tên/mã phí: `THEO_DIEN_TICH` lấy diện tích, `THEO_NHAN_KHAU` lấy số người, `THEO_CAN_HO` lấy 1. DAO đọc `phuong_thuc_tinh` thực từ `fee_types`. Fixture `BillingMySqlIT` lưu PHI_DV/PHI_QL = THEO_DIEN_TICH và PHI_VS = THEO_NHAN_KHAU; ca E2E thật cho đúng 1.384.000 và CHUA_DONG. Điều đó chứng minh cấu hình được kiểm và đường tính tiền, không chứng minh mọi DB ngoài môi trường audit đều được cấu hình đúng.

**Kiểm tra seed:** các file SQL tracked trong `database/` tạo schema fee_types/fee_rates nhưng không seed ba khoản BR-08/giá có ngày hiệu lực. Seed users và hộ/nhân khẩu không phải seed phí. Không có implicit seed dates trong migration US-006. Vì vậy cần cấu hình khoản thu/giá đúng bằng US-005/006 trước demo; không được coi fixture test là seed production. Không đọc hoặc sửa DB demo để tự tuyên bố cấu hình demo đã đúng.

README mục 1 và USER_GUIDE mục 7 ghi phí dịch vụ theo nhân khẩu, trái SRS/WBS và hành vi đã kiểm. Đây là **CONFIRMED GAP của tài liệu**, cần sửa riêng; không có căn cứ đổi BillingCalculator sang nhân khẩu. Chức năng data-driven vẫn có thể tính sai nghiệp vụ nếu người cấu hình chọn sai method cho PHI_DV; hệ thống không tự override theo mã phí.

### US-014 AC1: FAIL / CONFIRMED GAP so với bảng PO

SRS EP01 gọi `ma_ho` là **“Mã hộ khẩu”**, định danh duy nhất, ví dụ HK-1002. SRS EP07 §1.1 chỉ liệt kê số căn hộ, tên cư dân, CCCD/CMND và tầng; không định nghĩa một trường độc lập tên **“số hộ khẩu”**, quy tắc nhập, cách tìm hoặc sự tương đương với `ma_ho`. WBS `03 HOP DONG TICH HOP!C18` cũng liệt kê phòng/tên/CCCD/điện thoại, không giải quyết định nghĩa này.

Schema households có `ma_ho`, không có trường số sổ hộ khẩu riêng. Enum criteria chỉ có APARTMENT, NAME, CCCD, FLOOR; Service chỉ chuẩn hóa/validate bốn lựa chọn đó; DAO chỉ có bốn predicate tương ứng. JOIN/SELECT `h.ma_ho` để trả kết quả và mở detail không phải predicate tìm kiếm. ComboBox UI lấy chính enum này. Các ca MySQL đã chạy chứng minh phòng/tên và điều hướng/deny, không có ca nào tìm theo số hộ khẩu. Không tạo test “PASS” bằng cách thay số hộ khẩu thành CCCD hoặc maHo.

Khoảng trống chức năng là chắc chắn đối với câu PO cung cấp, dù định nghĩa dữ liệu còn cần làm rõ trước khi sửa. Giữ trạng thái **FAIL**, không dùng việc SRS cũ thiếu trường để tự hạ yêu cầu mới hoặc đánh dấu hoàn thành. PO cần chốt thuật ngữ, nguồn dữ liệu, ví dụ, chính xác/prefix và quyền tra cứu. Nhánh RESIDENT-own còn bị deny vì chưa có trusted ownership mapping; handoff US-014 ghi rõ PO đã đồng ý defer. Không suy quyền từ username/maHo.

## 6. Tổng hợp và việc cần quyết định

| Trạng thái AC | Số lượng |
|---|---:|
| PASS | **26** |
| FAIL | **1** |
| NEEDS VERIFICATION | **0** |
| REQUIREMENT AMBIGUITY | **1** |
| Tổng | **28** |

Trạng thái theo nhóm ban đầu của PO: **11 vàng → 11 PASS**; **2 đỏ → 1 PASS công thức, 1 FAIL tra cứu**; **15 xanh → 14 PASS, 1 REQUIREMENT AMBIGUITY tên Admin**. PASS ở đây là cho các tình huống/ràng buộc nêu trong ma trận trên baseline tích hợp, không phải chứng nhận mọi BR mở rộng, mọi thiết bị, hoặc tất cả luồng thủ công đã hoàn tất.

| Ưu tiên | Phát hiện | Tác động demo/bảo vệ | Việc cần làm sau audit |
|---|---|---|---|
| P1 chức năng | US-014 AC1 thiếu tìm theo số hộ khẩu | Không thể demo trọn câu AC PO yêu cầu | PO chốt nghĩa/trường trước; sau đó hotfix query/UI/test theo contract được duyệt. Không tự sửa DB trong audit. |
| P1 tài liệu | README/USER_GUIDE mô tả phí dịch vụ theo nhân khẩu | Có thể hướng dẫn cấu hình sai, giải thích sai số tiền khi bảo vệ | Sửa tài liệu theo SRS/DEC-08, không đổi công thức. Xác minh method/rate/ngày trong DB demo trước buổi bảo vệ. |
| P1 quyết định/test | US-012 AC1: tên seed mới khác literal Admin của SRS/test, một test baseline đỏ | Báo cáo “100% xanh” sẽ không đúng; không đồng nghĩa sai mật khẩu vẫn được vào | Lead/PO xác nhận tên hiển thị là dữ liệu mẫu hay literal; sửa đúng seed/test/tài liệu bằng PR riêng. |
| P1 tích hợp | #30/#31 chưa merge | main không phải phiên bản đã audit; chạy main có thể thiếu layout/INITIAL hotfix | Review/merge theo quy trình của Lead; kiểm tra lại SHA/tree triển khai sau merge. Không tự merge. |
| P2 phạm vi/tài liệu | USER_GUIDE còn ghi ACCOUNTANT không có một số quyền mà EP06 cho phép; README nói có phone search/DAO RBAC | Demo nhầm vai trò hoặc tuyên bố quá mức | Sửa mô tả theo ma trận thực, ghi rõ Service guard; phone/RESIDENT-own và credit carry-forward/native PDF theo quyết định phạm vi, không tự coi đã đạt toàn SRS. |

Không phát hiện lỗi P0 về ghi dữ liệu sai hoặc vượt quyền trong các ca đã chạy. Không có thay đổi nghiệp vụ/database/UI/AC trong PR báo cáo. Không dùng kết quả này để tuyên bố các nhánh hoãn (ownership adapter, tự tiêu thụ credit, native PDF) đã được triển khai.

## 7. Tái lập audit

Tích hợp tạm lại đúng ba parent ở worktree riêng:

```powershell
git worktree add --detach <audit-dir> c8df66e0829fde17f290c1269b82365ed60e23b2
git -C <audit-dir> merge --no-ff --no-edit 1aca7eafbaa87d5a4889d5f6bb260f40dd2143b2 576663f99d3b355d4ef6c0e65cb4ab8d45e80685
git -C <audit-dir> rev-parse 'HEAD^{tree}'
```

Tree phải bằng `80b85b96e64971289e5743c5afe81f641e44bebd`; commit merge dựng lại có thể khác SHA vì thời điểm/metadata commit. Baseline `eb369f7` chỉ tồn tại trong worktree audit local, không push merge tạm vào main hay PR tài liệu.

Đặt JDK17, Maven3.9.9 và cấu hình DB cô lập hiện hành; selectors chính xác trong manifest. Dùng `mvn -o -Dtest=<selector> clean test` cho U và `mvn -o -Dtest=<selector> test` với `BLUEMOON_MYSQL_TEST=1` cho M. Muốn chạy probe, sao chép nguồn kèm theo vào `src/test/java/com/bluemoon/services/` của worktree kiểm tra, dùng đúng schema `bluemoon_audit28_probe_test`, chạy `-Dtest=Sprint1AuditProbeTest`; probe có bảo vệ tên catalog trước khi dọn fixture. Chỉ dọn bản sao probe sau khi hoàn tất. Không cần sửa hoặc ghi đè test sản phẩm.

Nguồn raw log/XML local ở `.tools/audit-unit.log`, `.tools/audit-mysql.log`, `.tools/audit-probe.log`, `.tools/audit-login-probe.log`, `.tools/audit-resident-probe.log` và worktree `target/surefire-reports`; manifest giữ kết quả cần thiết để review mà không commit config/secret hoặc dữ liệu cá nhân.

<!-- Source links pinned to the exact file content used by the integrated baseline. -->
[AuthMySqlIT]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/test/java/com/bluemoon/services/AuthMySqlIT.java#L25
[AuthService]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/AuthService.java#L15
[BillingCalculator]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/BillingCalculator.java#L25
[BillingController]: https://github.com/ngocdg103/it3180-SE02/blob/1aca7eafbaa87d5a4889d5f6bb260f40dd2143b2/src/main/java/com/bluemoon/controllers/BillingController.java#L56
[BillingDAO]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/dao/BillingDAO.java#L30
[BillingServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/BillingServiceImpl.java#L31
[DashboardController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/DashboardController.java#L12
[DebtController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/DebtController.java#L16
[DebtDAO]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/dao/DebtDAO.java#L10
[DebtServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/DebtServiceImpl.java#L10
[FeeCategoryController]: https://github.com/ngocdg103/it3180-SE02/blob/576663f99d3b355d4ef6c0e65cb4ab8d45e80685/src/main/java/com/bluemoon/controllers/FeeCategoryController.java#L46
[FeeRateController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/FeeRateController.java#L22
[FeeRateDAO]: https://github.com/ngocdg103/it3180-SE02/blob/576663f99d3b355d4ef6c0e65cb4ab8d45e80685/src/main/java/com/bluemoon/dao/FeeRateDAO.java#L10
[FeeRateServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/FeeRateServiceImpl.java#L13
[FeeTypeDAO]: https://github.com/ngocdg103/it3180-SE02/blob/576663f99d3b355d4ef6c0e65cb4ab8d45e80685/src/main/java/com/bluemoon/dao/FeeTypeDAO.java#L9
[FeeTypeServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/576663f99d3b355d4ef6c0e65cb4ab8d45e80685/src/main/java/com/bluemoon/services/FeeTypeServiceImpl.java#L11
[HouseholdDAO]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/dao/HouseholdDAO.java#L19
[HouseholdFormController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/HouseholdFormController.java#L13
[HouseholdNavigator]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/views/HouseholdNavigator.java#L20
[HouseholdServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/HouseholdServiceImpl.java#L27
[LoginController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/LoginController.java#L11
[NoticeController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/NoticeController.java#L54
[NoticeFormatter]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/NoticeFormatter.java#L13
[NoticeServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/NoticeServiceImpl.java#L18
[ProductionModules]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/views/ProductionModules.java#L17
[ReceiptController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/ReceiptController.java#L18
[ReceiptDAO]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/dao/ReceiptDAO.java#L12
[ReceiptServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/ReceiptServiceImpl.java#L11
[ResidentDAO]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/dao/ResidentDAO.java#L22
[ResidentPanelController]: https://github.com/ngocdg103/it3180-SE02/blob/1aca7eafbaa87d5a4889d5f6bb260f40dd2143b2/src/main/java/com/bluemoon/controllers/ResidentPanelController.java#L14
[ResidentSearchController]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/controllers/ResidentSearchController.java#L14
[ResidentSearchCriteria]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/models/ResidentSearchCriteria.java#L4
[ResidentSearchServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/ResidentSearchServiceImpl.java#L9
[ResidentServiceImpl]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/ResidentServiceImpl.java#L29
[RoleBasedAuthorizationService]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/src/main/java/com/bluemoon/services/RoleBasedAuthorizationService.java#L17
[Seed]: https://github.com/ngocdg103/it3180-SE02/blob/c8df66e0829fde17f290c1269b82365ed60e23b2/database/seed.sql#L1
