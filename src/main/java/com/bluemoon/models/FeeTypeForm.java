package com.bluemoon.models;

/**
 * Raw input for createFeeType/updateFeeType. Strings come straight from the input fields;
 * null or blank means empty, a null enum means "not chosen". The Service validates independently.
 * soTienMacDinh uses a dot as decimal separator (for example "0" or "7000.50").
 */
public record FeeTypeForm(String maKhoanThu, String tenKhoanThu, FeeKind loai,
                          ChargeMethod hinhThuc, String soTienMacDinh, FeeStatus trangThai) { }
