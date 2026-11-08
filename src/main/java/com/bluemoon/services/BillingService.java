package com.bluemoon.services;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingPeriodDetail;
import com.bluemoon.models.BillingPreview;
import com.bluemoon.models.MonthlyCharge;
import java.util.List;

/**
 * US-007 contract (see docs/design/us-007-backend-contract-review.md, Revision 02).
 * Permissions: preview/generate need BILLING_PERIOD_CREATE; list/get need DEBT_REPORT_VIEW (ADMIN and ACCOUNTANT).
 * A missing session or role throws AccessDeniedException, checked before validation and storage access.
 * ky_thu is "YYYY-MM". previewPeriod and generatePeriod accept only the current month by the service Clock (Q11);
 * use getPeriod/getInvoice for older periods. Amounts are BigDecimal, whole VND, rounded HALF_UP before saving.
 * Calls may block on JDBC: do not call from the JavaFX Application Thread.
 */
public interface BillingService {
    /** Read-only. If the period already exists, returns the stored snapshot with alreadyGenerated = true. */
    BillingResult<BillingPreview> previewPeriod(String kyThu);

    /**
     * Creates the period and all its charges in one transaction (all or nothing). A second call for the same
     * ky_thu returns DUPLICATE ("Kỳ thu 2026-11 đã tồn tại trên hệ thống!") and writes nothing.
     */
    BillingResult<BillingPeriod> generatePeriod(String kyThu);

    /** Newest first. An empty list is still SUCCESS. */
    BillingResult<List<BillingPeriod>> listPeriods();

    /** NOT_FOUND if the period does not exist. Charges are header level (no lines). */
    BillingResult<BillingPeriodDetail> getPeriod(String kyThu);

    /**
     * Stored invoices of one apartment, newest period first (US-001 BR-02 / Household detail). Header level, no lines,
     * read from the issued snapshot only: nothing is recomputed. daThanhToan/conThieu/trangThai are the stored values
     * (CHUA_DONG until US-008 records payments); they are not inferred here. An apartment never billed is SUCCESS with
     * an empty list. Blank soCanHo is VALIDATION_ERROR. Needs DEBT_REPORT_VIEW (ADMIN, ACCOUNTANT).
     */
    BillingResult<List<MonthlyCharge>> getApartmentHistory(String soCanHo);

    /** NOT_FOUND if the invoice does not exist. Includes all lines. */
    BillingResult<MonthlyCharge> getInvoice(String maHoaDon);
}
