package com.bluemoon.controllers;

import com.bluemoon.models.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MemoryBillingHistoryTest {
    private MonthlyCharge invoice(String period,String apartment) {
        return new MonthlyCharge("HD-"+period.replace("-","")+"-"+apartment,period,"HK-"+apartment,apartment,
                new BigDecimal("80"),2,new BigDecimal("123000"),BigDecimal.ZERO,new BigDecimal("123000"),
                ChargeStatus.CHUA_DONG,LocalDate.parse(period+"-28"),List.of());
    }
    @Test void fixtureFiltersApartmentOrdersNewestFirstAndPreservesSnapshot() {
        var repository=new MemoryBillingRepository();
        var old=invoice("2026-01","P1204"); var latest=invoice("2026-02","P1204");
        repository.invoices.addAll(List.of(old,invoice("2026-03","P1205"),latest));
        var result=repository.findChargesByApartment("P1204");
        assertEquals(List.of(latest,old),result); assertTrue(result.stream().allMatch(c -> c.lines().isEmpty()));
        assertTrue(repository.findChargesByApartment("P9999").isEmpty()); assertEquals(3,repository.invoices.size());
    }
}
