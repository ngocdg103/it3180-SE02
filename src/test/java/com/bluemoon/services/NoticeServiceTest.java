package com.bluemoon.services;

import static com.bluemoon.services.BillingResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.models.Account;
import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingPeriodDetail;
import com.bluemoon.models.BillingPreview;
import com.bluemoon.models.ChargeLine;
import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.ChargeStatus;
import com.bluemoon.models.MonthlyCharge;
import com.bluemoon.models.NoticeSlip;
import com.bluemoon.models.Role;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Notice building over stored invoices: nothing is recomputed, nothing is stored. */
class NoticeServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DUE = LocalDate.of(2026, 12, 15);
    private final Stub billing = new Stub();

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static ChargeLine line(String code, ChargeMethod method, String qty, String rate, String amount) {
        return new ChargeLine(code, "Khoan " + code, method, d(qty), d(rate), LocalDate.of(2026, 1, 1), d(amount));
    }

    private static MonthlyCharge invoice(String apartment, List<ChargeLine> lines, String total) {
        return new MonthlyCharge("HD-202611-" + apartment, "2026-11", "HK-" + apartment.substring(1), apartment,
                d("80"), 4, d(total), d("0"), d(total), ChargeStatus.CHUA_DONG, DUE, lines);
    }

    private static MonthlyCharge ac01(String apartment) {
        return invoice(apartment, List.of(line("PHI_DV", ChargeMethod.THEO_DIEN_TICH, "80", "10000", "800000"),
                line("PHI_QL", ChargeMethod.THEO_DIEN_TICH, "80", "7000", "560000"),
                line("PHI_VS", ChargeMethod.THEO_NHAN_KHAU, "4", "6000", "24000")), "1384000");
    }

    private NoticeServiceImpl service(Role role) {
        var account = (java.util.function.Supplier<Optional<Account>>) () -> role == null ? Optional.empty()
                : Optional.of(new Account(1, "u", "U", role));
        return new NoticeServiceImpl(billing, maHo -> Optional.of("Nguyễn Văn A"),
                new RoleBasedAuthorizationService(account, null), CLOCK);
    }

    private NoticeServiceImpl service() { return service(Role.ACCOUNTANT); }

    @Test void ac01NoticeMatchesTheStoredInvoiceExactly() {
        billing.add(ac01("P1204"));
        var slip = service().getNotice("2026-11", "P1204").data().orElseThrow();
        assertEquals("TB-202611-P1204", slip.maThongBao());
        assertEquals(new BigDecimal("1384000.00"), slip.tongTien());
        assertEquals(billing.invoices.get("HD-202611-P1204").tongTien(), slip.tongTien());
        assertEquals(billing.invoices.get("HD-202611-P1204").lines(), slip.lines(), "lines are the snapshot, not recomputed");
        assertEquals(DUE, slip.hanNop());
        assertEquals(LocalDate.of(2026, 11, 8), slip.ngayPhatHanh());
        assertEquals("P1204 Dong phi thang 11/2026", slip.noiDungChuyenKhoan());
        assertEquals("Nguyễn Văn A", slip.tenChuHo());
        assertEquals(0, slip.dienTich().compareTo(d("80")));
        assertEquals(4, slip.soNhanKhau());
    }

    @Test void issueDateUsesVietnamTime() {
        billing.add(ac01("P1204"));
        var clock = Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), ZoneOffset.UTC); // 8 Nov 01:00 in Vietnam
        var svc = new NoticeServiceImpl(billing, maHo -> Optional.empty(),
                new RoleBasedAuthorizationService(() -> Optional.of(new Account(1, "u", "U", Role.ADMIN)), null), clock);
        assertEquals(LocalDate.of(2026, 11, 8), svc.getNotice("2026-11", "P1204").data().orElseThrow().ngayPhatHanh());
    }

    @Test void periodNotGeneratedIsRefusedWithTheSrsMessage() {
        var result = service().getNotice("2026-12", "P1204");
        assertEquals(NOT_FOUND, result.status());
        assertEquals("Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!", result.message());
        assertEquals(NOT_FOUND, service().getNotices("2026-12").status());
        assertEquals("Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!",
                service().getNotices("2026-12").message());
    }

    @Test void validationAndMissingApartment() {
        billing.add(ac01("P1204"));
        assertEquals(VALIDATION_ERROR, service().getNotice("2026-1", "P1204").status());
        assertEquals(VALIDATION_ERROR, service().getNotice(null, "P1204").status());
        assertEquals(VALIDATION_ERROR, service().getNotice("2026-11", " ").status());
        assertEquals(VALIDATION_ERROR, service().getNotices("garbage").status());
        var missing = service().getNotice("2026-11", "P9999");
        assertEquals(NOT_FOUND, missing.status());
        assertEquals(NOT_FOUND, service().getNotice("2026-11", "P9999").status());
    }

    @Test void residentAndNoSessionAreDeniedBeforeAnyRead() {
        billing.add(ac01("P1204"));
        for (Role role : new Role[] {Role.RESIDENT, null}) {
            assertThrows(AccessDeniedException.class, () -> service(role).getNotice("2026-11", "P1204"));
            assertThrows(AccessDeniedException.class, () -> service(role).getNotices("2026-11"));
        }
        assertEquals(0, billing.calls);
        assertDoesNotThrow(() -> service(Role.ADMIN).getNotices("2026-11"));
    }

    @Test void wholePeriodReturnsEveryHouseholdInOrder() {
        billing.add(ac01("P1101"));
        billing.add(ac01("P1204"));
        var slips = service().getNotices("2026-11").data().orElseThrow();
        assertEquals(List.of("P1101", "P1204"), slips.stream().map(NoticeSlip::soCanHo).toList());
    }

    @Test void missingHeadIsNotAnError() {
        billing.add(ac01("P1204"));
        var svc = new NoticeServiceImpl(billing, maHo -> Optional.empty(),
                new RoleBasedAuthorizationService(() -> Optional.of(new Account(1, "u", "U", Role.ADMIN)), null), CLOCK);
        assertNull(svc.getNotice("2026-11", "P1204").data().orElseThrow().tenChuHo());
    }

    @Test void failuresAreGenericAndNeverPartial() {
        billing.add(ac01("P1204"));
        var failingHeads = new NoticeServiceImpl(billing, maHo -> { throw new SQLException("secret", "HY000", 1); },
                new RoleBasedAuthorizationService(() -> Optional.of(new Account(1, "u", "U", Role.ADMIN)), null), CLOCK);
        var result = failingHeads.getNotices("2026-11");
        assertEquals(SYSTEM_ERROR, result.status());
        assertFalse(result.message().contains("secret"));
        billing.failure = BillingResult.failure(SYSTEM_ERROR, "Lỗi hệ thống, vui lòng thử lại sau.", Map.of());
        assertEquals(SYSTEM_ERROR, service().getNotice("2026-11", "P1204").status());
        assertEquals(SYSTEM_ERROR, service().getNotices("2026-11").status());
    }

    // ---- stub: a scripted BillingService over stored invoices ----
    private static final class Stub implements BillingService {
        final Map<String, MonthlyCharge> invoices = new java.util.TreeMap<>();
        BillingResult<?> failure;
        int calls;

        void add(MonthlyCharge charge) { invoices.put(charge.maHoaDon(), charge); }

        @Override public BillingResult<BillingPreview> previewPeriod(String kyThu) { throw new UnsupportedOperationException(); }
        @Override public BillingResult<BillingPeriod> generatePeriod(String kyThu) { throw new UnsupportedOperationException(); }
        @Override public BillingResult<List<BillingPeriod>> listPeriods() { throw new UnsupportedOperationException(); }

        // Incoming PR #25 contract; also compiles against the pre-merge BillingService interface.
        @SuppressWarnings("unchecked") public BillingResult<List<MonthlyCharge>> getApartmentHistory(String soCanHo) {
            calls++;
            if (failure != null) { return (BillingResult<List<MonthlyCharge>>) failure; }
            var headers=invoices.values().stream().filter(c -> c.soCanHo().equals(soCanHo))
                    .sorted((a,b) -> b.kyThu().compareTo(a.kyThu()))
                    .map(c -> new MonthlyCharge(c.maHoaDon(),c.kyThu(),c.maHo(),c.soCanHo(),c.dienTich(),
                            c.soNhanKhau(),c.tongTien(),c.daThanhToan(),c.conThieu(),c.trangThai(),c.hanNop(),List.of()))
                    .toList();
            return BillingResult.success("OK",headers);
        }

        @SuppressWarnings("unchecked") @Override public BillingResult<BillingPeriodDetail> getPeriod(String kyThu) {
            calls++;
            if (failure != null) { return (BillingResult<BillingPeriodDetail>) failure; }
            var headers = new ArrayList<MonthlyCharge>();
            invoices.values().stream().filter(c -> c.kyThu().equals(kyThu)).forEach(c -> headers.add(new MonthlyCharge(
                    c.maHoaDon(), c.kyThu(), c.maHo(), c.soCanHo(), c.dienTich(), c.soNhanKhau(), c.tongTien(),
                    c.daThanhToan(), c.conThieu(), c.trangThai(), c.hanNop(), List.of())));
            if (headers.isEmpty()) { return BillingResult.failure(NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu.", Map.of()); }
            return BillingResult.success("OK", new BillingPeriodDetail(
                    new BillingPeriod(kyThu, DUE, headers.size(), 0, d("0"), Instant.EPOCH), headers));
        }

        @Override public BillingResult<MonthlyCharge> getInvoice(String maHoaDon) {
            calls++;
            var found = invoices.get(maHoaDon);
            return found == null ? BillingResult.failure(NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu.", Map.of())
                    : BillingResult.success("OK", found);
        }
    }
}
