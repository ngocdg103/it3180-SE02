package com.bluemoon.models;

import java.math.BigDecimal;

/** Outstanding debt and surplus credit are distinct; credit is not silently applied to another invoice. */
public record HouseholdDebt(String maHo, BigDecimal tongPhaiThu, BigDecimal tongDaThu, BigDecimal tongNo, BigDecimal soDu) { }
