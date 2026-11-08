package com.bluemoon.models;

import java.math.BigDecimal;

/** Resident is null for an apartment without members (apartment/floor search still finds it). */
public record ResidentSearchHit(String maHo,String soCanHo,int tang,BigDecimal dienTich,
                                ApartmentStatus trangThai,String tenChuHo,Resident resident) { }
