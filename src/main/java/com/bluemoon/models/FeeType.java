package com.bluemoon.models;

import java.math.BigDecimal;

/** A stored fee category (khoan thu). soTienMacDinh is not an official rate; US-006 owns unit prices. */
public record FeeType(String maKhoanThu, String tenKhoanThu, FeeKind loai,
                      ChargeMethod hinhThuc, BigDecimal soTienMacDinh, FeeStatus trangThai) { }
