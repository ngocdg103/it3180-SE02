package com.bluemoon.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeRateTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private FeeRate rate(Long id, String code, String price, LocalDate date, String note) {
        return new FeeRate(id, code, price == null ? null : new BigDecimal(price), date, note);
    }
    @Test void normalizesExactlyAndAllowsFullRange() {
        assertEquals(new BigDecimal("1.23"), rate(null, "PHI", "1.230", DAY, null).donGia());
        assertEquals(new BigDecimal("1.00"), rate(1L, "A_1", "1", DAY, "😀".repeat(255)).donGia());
        assertNotNull(rate(null, "X".repeat(20), "99999999999999999.99", LocalDate.of(9999,12,31), null));
        assertNotNull(rate(null, "X", "0.01", LocalDate.of(1000,1,1), null));
    }
    @Test void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> rate(null,null,"1",DAY,null));
        assertThrows(NullPointerException.class, () -> rate(null,"X",null,DAY,null));
        assertThrows(NullPointerException.class, () -> rate(null,"X","1",null,null));
    }
    @Test void rejectsInvalidValuesWithoutRounding() {
        for (String price : new String[]{"0", "-1", "0.001", "1.234", "100000000000000000.00"}) {
            assertThrows(IllegalArgumentException.class, () -> rate(null,"X",price,DAY,null));
        }
        for (String code : new String[]{"", "lower", " X", "X' OR 1=1", "X".repeat(21), "PHÍ"}) {
            assertThrows(IllegalArgumentException.class, () -> rate(null,code,"1",DAY,null));
        }
        for (long id : new long[]{0,-1}) { assertThrows(IllegalArgumentException.class, () -> rate(id,"X","1",DAY,null)); }
        assertThrows(IllegalArgumentException.class, () -> rate(null,"X","1",DAY,"😀".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> rate(null,"X","1",LocalDate.of(999,12,31),null));
        assertThrows(IllegalArgumentException.class, () -> rate(null,"X","1",LocalDate.of(10000,1,1),null));
    }
}
