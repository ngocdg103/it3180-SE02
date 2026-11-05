package com.bluemoon.models;

import java.time.LocalDate;

/** Table `households`: the household record (ma_ho) attached to exactly one apartment. */
public record Household(String maHo, String soCanHo, LocalDate ngayNhanNha) { }
