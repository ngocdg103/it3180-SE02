package com.bluemoon.services;

import com.bluemoon.models.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResidentSearchServiceTest {
    @AfterEach void clear(){FeeCategorySessionFixture.use(null);}
    @Test void normalizesEachCriterionAndUsesExistingStaffPermission() {
        for(Role role:List.of(Role.ADMIN,Role.ACCOUNTANT)) {
            FeeCategorySessionFixture.use(role);var seen=new ArrayList<ResidentSearchCriteria>();
            var service=new ResidentSearchServiceImpl(q -> {seen.add(q);return List.of();},new RoleBasedAuthorizationService(null));
            assertTrue(service.search(ResidentSearchCriteria.Field.APARTMENT," 1204 ").isSuccess());
            assertTrue(service.search(ResidentSearchCriteria.Field.NAME,"Đỗ Gia NgỌc").isSuccess());
            service.search(ResidentSearchCriteria.Field.CCCD,"001200001234");service.search(ResidentSearchCriteria.Field.FLOOR,"02");
            assertEquals(List.of("P1204","do gia ngoc","001200001234","2"),seen.stream().map(ResidentSearchCriteria::value).toList());
        }
    }
    @Test void deniedBeforeValidationAndRepository() {
        var calls=new AtomicInteger();var service=new ResidentSearchServiceImpl(q -> {calls.incrementAndGet();return List.of();},new RoleBasedAuthorizationService(null));
        for(Role role:new Role[]{null,Role.RESIDENT}) {FeeCategorySessionFixture.use(role);assertThrows(AccessDeniedException.class,() -> service.search(null,null));}
        assertEquals(0,calls.get());
    }
    @Test void invalidInputNeverQueriesAndEmptyResultUsesAc03Message() {
        FeeCategorySessionFixture.use(Role.ADMIN);var calls=new AtomicInteger();var service=new ResidentSearchServiceImpl(q -> {calls.incrementAndGet();return List.of();},new RoleBasedAuthorizationService(null));
        for(var field:ResidentSearchCriteria.Field.values())assertEquals(ResidentResult.Status.VALIDATION_ERROR,service.search(field," ").status());
        assertEquals(ResidentResult.Status.VALIDATION_ERROR,service.search(ResidentSearchCriteria.Field.FLOOR,"31").status());
        assertEquals(ResidentResult.Status.VALIDATION_ERROR,service.search(ResidentSearchCriteria.Field.APARTMENT,"P1204' OR 1=1").status());
        assertEquals(ResidentResult.Status.VALIDATION_ERROR,service.search(ResidentSearchCriteria.Field.CCCD,"12").status());assertEquals(0,calls.get());
        assertEquals("Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!",service.search(ResidentSearchCriteria.Field.APARTMENT,"P9999").message());
    }
    @Test void infrastructureFailureIsSanitized() {
        FeeCategorySessionFixture.use(Role.ADMIN);var service=new ResidentSearchServiceImpl(q -> {throw new java.sql.SQLException("password=secret SQL");},new RoleBasedAuthorizationService(null));
        var result=service.search(ResidentSearchCriteria.Field.NAME,"ngoc");assertEquals(ResidentResult.Status.SYSTEM_ERROR,result.status());assertFalse(result.message().contains("secret"));
    }
}
