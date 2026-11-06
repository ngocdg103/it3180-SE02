package com.bluemoon.dao;

import com.bluemoon.models.Apartment;
import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.Household;
import com.bluemoon.models.HouseholdProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * No-database implementation with the same uniqueness semantics as the schema.
 * For Service tests, and for Dev-2 to run the UI without MySQL:
 * new HouseholdServiceImpl(new InMemoryHouseholdRepository(), ...).
 */
public final class InMemoryHouseholdRepository implements HouseholdRepository {
    private final TreeMap<String, HouseholdProfile> byApartment = new TreeMap<>();

    @Override
    public synchronized void insert(HouseholdProfile profile) throws DuplicateRecordException {
        if (byApartment.containsKey(profile.soCanHo())) {
            throw new DuplicateRecordException(DuplicateRecordException.Field.SO_CAN_HO, null);
        }
        if (findByMaHo(profile.maHo()).isPresent()) {
            throw new DuplicateRecordException(DuplicateRecordException.Field.MA_HO, null);
        }
        byApartment.put(profile.soCanHo(), profile);
    }

    @Override
    public synchronized Optional<HouseholdProfile> findByMaHo(String maHo) {
        return byApartment.values().stream().filter(p -> p.maHo().equals(maHo)).findFirst();
    }

    @Override
    public synchronized Optional<HouseholdProfile> findBySoCanHo(String soCanHo) {
        return Optional.ofNullable(byApartment.get(soCanHo));
    }

    @Override
    public synchronized List<HouseholdProfile> findAll(ApartmentStatus status) {
        return new ArrayList<>(byApartment.values().stream()
                .filter(p -> status == null || p.trangThai() == status).toList());
    }

    @Override
    public synchronized boolean update(String maHo, int tang, BigDecimal dienTich, LocalDate ngayNhanNha,
                                       ApartmentStatus trangThai) {
        var current = findByMaHo(maHo);
        if (current.isEmpty()) { return false; }
        var soCanHo = current.get().soCanHo();
        byApartment.put(soCanHo, new HouseholdProfile(new Apartment(soCanHo, tang, dienTich, trangThai),
                new Household(maHo, soCanHo, ngayNhanNha)));
        return true;
    }
}
