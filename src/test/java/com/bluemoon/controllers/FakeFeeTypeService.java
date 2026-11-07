package com.bluemoon.controllers;

import com.bluemoon.models.ChargeMethod;
import com.bluemoon.models.FeeKind;
import com.bluemoon.models.FeeStatus;
import com.bluemoon.models.FeeType;
import com.bluemoon.models.FeeTypeForm;
import com.bluemoon.services.FeeTypeResult;
import com.bluemoon.services.FeeTypeService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * TEST-ONLY fake of the US-005 contract. It is NOT a backend: no validation, no persistence rules.
 * Behaviour is scripted by each test.
 */
final class FakeFeeTypeService implements FeeTypeService {
    final List<FeeType> stored = new CopyOnWriteArrayList<>();
    final List<FeeTypeForm> createdForms = new CopyOnWriteArrayList<>();
    final List<FeeStatus> listFilters = new CopyOnWriteArrayList<>();
    final AtomicInteger listCalls = new AtomicInteger();
    final AtomicInteger createCalls = new AtomicInteger();
    volatile RuntimeException listFailure;
    volatile RuntimeException createFailure;
    volatile CountDownLatch createGate;          // when set, createFeeType blocks until released
    volatile CountDownLatch listGate;
    volatile Thread lastCallThread;
    volatile Function<FeeTypeForm, FeeTypeResult<FeeType>> createScript;

    @Override public FeeTypeResult<List<FeeType>> listFeeTypes(FeeStatus filter) {
        lastCallThread = Thread.currentThread();
        listCalls.incrementAndGet();
        listFilters.add(filter);
        await(listGate);
        if (listFailure != null) { throw listFailure; }
        List<FeeType> rows = new ArrayList<>();
        for (FeeType fee : stored) { if (filter == null || fee.trangThai() == filter) { rows.add(fee); } }
        return FeeTypeResult.success("OK", rows);
    }

    @Override public FeeTypeResult<FeeType> createFeeType(FeeTypeForm form) {
        lastCallThread = Thread.currentThread();
        createCalls.incrementAndGet();
        createdForms.add(form);
        await(createGate);
        if (createFailure != null) { throw createFailure; }
        if (createScript != null) { return createScript.apply(form); }
        FeeType fee = new FeeType(form.maKhoanThu(), form.tenKhoanThu(), form.loai(), form.hinhThuc(),
                new BigDecimal(form.soTienMacDinh()), form.trangThai());
        stored.add(fee);
        return FeeTypeResult.success("Tạo khoản thu " + fee.maKhoanThu() + " thành công", fee);
    }

    @Override public FeeTypeResult<FeeType> getFeeType(String maKhoanThu) { throw new UnsupportedOperationException(); }
    @Override public FeeTypeResult<FeeType> updateFeeType(String maKhoanThu, FeeTypeForm form) { throw new UnsupportedOperationException(); }
    @Override public FeeTypeResult<FeeType> deactivateFeeType(String maKhoanThu) { throw new UnsupportedOperationException(); }

    static FeeType fee(String code, String name, FeeKind kind, ChargeMethod method, String amount, FeeStatus status) {
        return new FeeType(code, name, kind, method, new BigDecimal(amount), status);
    }

    static FeeTypeResult<FeeType> duplicate(String code) {
        return FeeTypeResult.failure(FeeTypeResult.Status.DUPLICATE, "Mã khoản thu đã tồn tại",
                Map.of("ma_khoan_thu", "Mã khoản thu không được để trống, không chứa khoảng trắng và không được trùng!"));
    }

    private static void await(CountDownLatch gate) {
        if (gate == null) { return; }
        try { gate.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
