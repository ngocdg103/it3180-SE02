package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Area/people are billing snapshots; owner name is current, as approved Q18 (no owner snapshot). */
public record DebtRow(String maHoaDon, String maHo, String soCanHo, int tang, String tenChuHo,
                      BigDecimal dienTich, int soNguoi, BigDecimal tongTien, BigDecimal daThu,
                      BigDecimal conThieu, ChargeStatus trangThai, LocalDate lanNopCuoi) { }
