package com.bluemoon.models;

import java.time.LocalDate;

/** requestId is generated once per intent and retained during reconciliation; never regenerate on an error. */
public record ReceiptForm(String requestId, String maHoaDon, String nguoiNop, String soTienNop,
                          LocalDate ngayNop, PaymentMethod hinhThuc, String ghiChu) { }
