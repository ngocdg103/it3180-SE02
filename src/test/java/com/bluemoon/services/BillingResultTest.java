package com.bluemoon.services;

import static com.bluemoon.services.BillingResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bluemoon.models.BillingPeriod;
import com.bluemoon.models.BillingPeriodDetail;
import com.bluemoon.models.BillingPreview;
import com.bluemoon.models.MonthlyCharge;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Contract checks for BillingResult and the BillingService signatures. No service implementation exists yet. */
class BillingResultTest {
    @Test void statusSetMatchesContract() {
        assertEquals(List.of("SUCCESS", "VALIDATION_ERROR", "DUPLICATE", "MISSING_RATE", "NOTHING_TO_BILL",
                "NOT_FOUND", "BUSY", "SYSTEM_ERROR"),
                java.util.Arrays.stream(BillingResult.Status.values()).map(Enum::name).toList());
    }

    @Test void successCarriesDataAndNoFieldErrors() {
        var result = BillingResult.success("ok", "payload");
        assertTrue(result.isSuccess());
        assertEquals(SUCCESS, result.status());
        assertEquals("payload", result.data().orElseThrow());
        assertEquals("ok", result.message());
        assertTrue(result.fieldErrors().isEmpty());
    }

    @Test void successRejectsNullData() {
        assertThrows(NullPointerException.class, () -> BillingResult.success("ok", null));
    }

    @ParameterizedTest @EnumSource(value = BillingResult.Status.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
    void failureHasNoDataAndKeepsFieldErrors(BillingResult.Status status) {
        var result = BillingResult.<String>failure(status, "bad", Map.of("ky_thu", "Kỳ thu 2026-11 đã tồn tại trên hệ thống!"));
        assertFalse(result.isSuccess());
        assertEquals(status, result.status());
        assertTrue(result.data().isEmpty());
        assertEquals("Kỳ thu 2026-11 đã tồn tại trên hệ thống!", result.fieldErrors().get("ky_thu"));
    }

    @Test void failureCannotUseSuccessStatus() {
        assertThrows(IllegalArgumentException.class, () -> BillingResult.failure(SUCCESS, "x", Map.of()));
    }

    @Test void failureRejectsNullStatusAndMessage() {
        assertThrows(NullPointerException.class, () -> BillingResult.failure(null, "x", Map.of()));
        assertThrows(NullPointerException.class, () -> BillingResult.failure(BUSY, null, Map.of()));
    }

    @Test void fieldErrorsAreCopiedAndUnmodifiable() {
        var source = new LinkedHashMap<String, String>();
        source.put("PHI_QL", "Thiếu đơn giá");
        var result = BillingResult.<String>failure(MISSING_RATE, "Thiếu đơn giá", source);
        source.put("PHI_DV", "late");
        assertEquals(List.of("PHI_QL"), List.copyOf(result.fieldErrors().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> result.fieldErrors().put("x", "y"));
    }

    @Test void billingServiceSignaturesMatchContract() throws Exception {
        assertSignature("previewPeriod", BillingPreview.class, String.class);
        assertSignature("generatePeriod", BillingPeriod.class, String.class);
        assertSignature("listPeriods", List.class);
        assertSignature("getPeriod", BillingPeriodDetail.class, String.class);
        assertSignature("getInvoice", MonthlyCharge.class, String.class);
        assertEquals(5, BillingService.class.getDeclaredMethods().length);
    }

    private static void assertSignature(String name, Class<?> payload, Class<?>... params) throws Exception {
        Method method = BillingService.class.getMethod(name, params);
        assertEquals(BillingResult.class, method.getReturnType(), name);
        var generic = (java.lang.reflect.ParameterizedType) method.getGenericReturnType();
        var arg = generic.getActualTypeArguments()[0];
        Class<?> raw = arg instanceof java.lang.reflect.ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) arg;
        assertEquals(payload, raw, name);
        assertEquals(0, method.getExceptionTypes().length, name + " must not declare checked exceptions");
    }
}
