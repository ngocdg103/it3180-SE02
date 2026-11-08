package com.bluemoon.models;

import java.util.Set;

/** null/blank period means latest; null/empty statuses means all. Bounds are inclusive. */
public record DebtQuery(String kyThu, Set<ChargeStatus> statuses, Integer tangTu, Integer tangDen) { }
