package com.bluemoon.services;

import com.bluemoon.dao.ResidentSearchRepository;
import com.bluemoon.models.*;
import java.text.Normalizer;
import java.util.*;

/** US-014 staff search; resident-own access awaits a trusted account/apartment mapping. */
public final class ResidentSearchServiceImpl implements ResidentSearchService {
    private final ResidentSearchRepository repository;
    private final AuthorizationService authorization;
    public ResidentSearchServiceImpl(ResidentSearchRepository repository,AuthorizationService authorization) {
        this.repository=Objects.requireNonNull(repository);this.authorization=Objects.requireNonNull(authorization);
    }
    public ResidentResult<List<ResidentSearchHit>> search(ResidentSearchCriteria.Field field,String keyword) {
        authorization.requirePermission(Permission.RESIDENT_PROFILE_VIEW);
        String value=keyword==null?"":keyword.trim();
        if(field==null || value.isEmpty() || value.length()>50)return invalid();
        switch(field) {
            case APARTMENT -> {value=value.toUpperCase(Locale.ROOT);if(!value.matches("P?[0-9]{1,4}"))return invalid();if(!value.startsWith("P"))value="P"+value;}
            case NAME -> value=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('đ','d');
            case CCCD -> {if(!value.matches("[0-9]{9}|[0-9]{12}"))return invalid();}
            case FLOOR -> {if(!value.matches("[0-9]{1,2}") || Integer.parseInt(value)<1 || Integer.parseInt(value)>30)return invalid();value=Integer.toString(Integer.parseInt(value));}
        }
        try {
            var hits=List.copyOf(repository.search(new ResidentSearchCriteria(field,value)));
            return ResidentResult.success(hits.isEmpty()?"Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!":"Tìm thấy "+hits.size()+" kết quả.",hits);
        }catch(java.sql.SQLException | RuntimeException failure) {
            return ResidentResult.failure(ResidentResult.Status.SYSTEM_ERROR,"Không tra cứu được. Vui lòng thử lại sau.",Map.of());
        }
    }
    private static <T> ResidentResult<T> invalid() {
        return ResidentResult.failure(ResidentResult.Status.VALIDATION_ERROR,"Vui lòng nhập từ khóa hợp lệ cho tiêu chí đã chọn.",Map.of("keyword","Từ khóa không hợp lệ."));
    }
}
