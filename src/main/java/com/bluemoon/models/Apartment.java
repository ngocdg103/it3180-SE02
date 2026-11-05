package com.bluemoon.models;

import java.math.BigDecimal;

/** Table `apartments`. dienTich is exact decimal (m2, 2 places) because EP-03 bills from it. */
public record Apartment(String soCanHo, int tang, BigDecimal dienTich, ApartmentStatus trangThai) { }
