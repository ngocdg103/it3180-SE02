package com.bluemoon.dao;

import com.bluemoon.models.ApartmentStatus;
import com.bluemoon.models.RelationToHead;
import com.bluemoon.models.Resident;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * No-database implementation with the same rules as ResidentDAO and the schema (one lock = synchronized).
 * For Service tests, and for Dev-2 to run the UI without MySQL:
 * new ResidentServiceImpl(new InMemoryResidentRepository(households), households, ...).
 */
public final class InMemoryResidentRepository implements ResidentRepository {
    private final HouseholdRepository households;
    private final List<Resident> rows = new ArrayList<>();
    private long nextId = 1;

    public InMemoryResidentRepository(HouseholdRepository households) {
        this.households = Objects.requireNonNull(households);
    }

    @Override
    public synchronized InsertOutcome insert(Resident resident, HeadPolicy policy, Long expectedHeadId)
            throws SQLException, ResidentDuplicateException {
        var profile = households.findByMaHo(resident.maHo());
        if (profile.isEmpty()) { return new HouseholdMissing(); }
        if (profile.get().trangThai() == ApartmentStatus.CAN_HO_TRONG) { return new ApartmentVacant(); }
        var members = findByMaHo(resident.maHo());
        if (resident.quanHeChuHo() != RelationToHead.CHU_HO && members.isEmpty()) { return new FirstMemberMustBeHead(); }

        Resident demote = null;
        if (resident.quanHeChuHo() == RelationToHead.CHU_HO) {
            var head = findHead(resident.maHo());
            if (head.isPresent()) {
                if (expectedHeadId == null || policy == HeadPolicy.REJECT_IF_HEAD_EXISTS) {
                    return new HeadExists(head.get());
                }
                if (expectedHeadId != head.get().maNhanKhau()) { return new HeadChanged(head); }
                demote = head.get();
            } else if (expectedHeadId != null && policy == HeadPolicy.REPLACE_HEAD_IF_UNCHANGED) {
                return new HeadChanged(Optional.empty());
            }
        }
        // Unique keys are checked before any change, so a failure leaves nothing half-written.
        for (var existing : rows) {
            if (resident.soCccd() != null && resident.soCccd().equals(existing.soCccd())) {
                throw new ResidentDuplicateException(ResidentDuplicateException.Field.SO_CCCD, null);
            }
            if (resident.soCccd() == null && existing.soCccd() == null && sameHousehold(resident, existing)
                    && sameName(resident.hoTen(), existing.hoTen())
                    && resident.ngaySinh().equals(existing.ngaySinh()) && resident.gioiTinh() == existing.gioiTinh()) {
                throw new ResidentDuplicateException(ResidentDuplicateException.Field.PERSON, null);
            }
        }
        if (demote != null) { replace(demote, withRelation(demote, RelationToHead.KHAC)); }
        var saved = new Resident(nextId++, resident.maHo(), resident.hoTen(), resident.ngaySinh(),
                resident.gioiTinh(), resident.soCccd(), resident.quanHeChuHo(), resident.ngayChuyenDen());
        rows.add(saved);
        return new Added(saved);
    }

    @Override
    public synchronized List<Resident> findByMaHo(String maHo) {
        return rows.stream().filter(r -> r.maHo().equals(maHo))
                .sorted(Comparator.comparing((Resident r) -> !r.isHead()).thenComparingLong(Resident::maNhanKhau))
                .toList();
    }

    @Override
    public synchronized Optional<Resident> findHead(String maHo) {
        return rows.stream().filter(r -> r.maHo().equals(maHo) && r.isHead()).findFirst();
    }

    @Override
    public synchronized int countByMaHo(String maHo) { return findByMaHo(maHo).size(); }

    private static boolean sameHousehold(Resident a, Resident b) { return a.maHo().equals(b.maHo()); }

    /** Case-insensitive, accent-sensitive, like the as_ci collation of the schema key. */
    private static boolean sameName(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static Resident withRelation(Resident r, RelationToHead relation) {
        return new Resident(r.maNhanKhau(), r.maHo(), r.hoTen(), r.ngaySinh(), r.gioiTinh(), r.soCccd(), relation,
                r.ngayChuyenDen());
    }

    private void replace(Resident old, Resident updated) { rows.set(rows.indexOf(old), updated); }
}
