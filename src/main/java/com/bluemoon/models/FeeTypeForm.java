package com.bluemoon.models;

/**
 * Raw input for createFeeType/updateFeeType. Strings come straight from the input fields;
 * null or blank means empty, a null enum means "not chosen". The Service validates independently.
 * soTienMacDinh uses a dot as decimal separator (for example "0" or "7000.50").
 * ngayApDungBanDau is chosen explicitly on create when the amount is positive; never defaulted.
 * It is not used by category metadata updates; append later prices through FeeRateService.
 */
public record FeeTypeForm(String maKhoanThu, String tenKhoanThu, FeeKind loai,
                          ChargeMethod hinhThuc, String soTienMacDinh, FeeStatus trangThai,
                          java.time.LocalDate ngayApDungBanDau) {
    /** Existing callers may create unpriced categories or update metadata without a rate date. */
    public FeeTypeForm(String maKhoanThu, String tenKhoanThu, FeeKind loai,
                       ChargeMethod hinhThuc, String soTienMacDinh, FeeStatus trangThai) {
        this(maKhoanThu, tenKhoanThu, loai, hinhThuc, soTienMacDinh, trangThai, null);
    }
}
