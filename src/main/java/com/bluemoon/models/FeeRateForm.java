package com.bluemoon.models;

import java.time.LocalDate;

/** Raw form values; validation belongs to FeeRateService, not the JavaFX controls. */
public record FeeRateForm(String maKhoanThu, String donGia, LocalDate ngayApDung, String ghiChu) { }
