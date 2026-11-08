package com.bluemoon.services;

import com.bluemoon.models.NoticeSlip;
import java.util.List;

/**
 * US-011 contract. Read-only over stored US-007 invoices; it never computes money and never stores anything.
 * Permission: DEBT_REPORT_VIEW (ADMIN, ACCOUNTANT). A missing session or role throws AccessDeniedException.
 * Results reuse BillingResult (VALIDATION_ERROR, NOT_FOUND, SYSTEM_ERROR, BUSY). Calls block on JDBC.
 */
public interface NoticeService {
    /** BR-16 / AC-02: NOT_FOUND "Chưa có dữ liệu hóa đơn cho kỳ thu này. Vui lòng lập kỳ thu trước!" if no period. */
    BillingResult<NoticeSlip> getNotice(String kyThu, String soCanHo);

    /** Every household of a generated period, ordered by apartment. Same NOT_FOUND rule for a missing period. */
    BillingResult<List<NoticeSlip>> getNotices(String kyThu);
}
