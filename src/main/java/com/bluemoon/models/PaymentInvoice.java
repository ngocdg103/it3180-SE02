package com.bluemoon.models;

import java.math.BigDecimal;

/** Payment-facing operational header; immutable billed totals are never recalculated by US-008. */
public record PaymentInvoice(String maHoaDon, String kyThu, String maHo, String soCanHo, BigDecimal tongTien,
                             BigDecimal daThanhToan, BigDecimal conThieu, ChargeStatus trangThai) { }
