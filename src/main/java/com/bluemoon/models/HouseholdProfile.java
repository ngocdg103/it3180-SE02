package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Read/write unit of US-001: a household with its linked apartment. Flat accessors are for table binding. */
public record HouseholdProfile(Apartment apartment, Household household) {
    public HouseholdProfile {
        Objects.requireNonNull(apartment);
        Objects.requireNonNull(household);
        if (!apartment.soCanHo().equals(household.soCanHo())) {
            throw new IllegalArgumentException("Household must reference its apartment");
        }
    }

    public String maHo() { return household.maHo(); }
    public String soCanHo() { return apartment.soCanHo(); }
    public int tang() { return apartment.tang(); }
    public BigDecimal dienTich() { return apartment.dienTich(); }
    public LocalDate ngayNhanNha() { return household.ngayNhanNha(); }
    public ApartmentStatus trangThai() { return apartment.trangThai(); }
}
