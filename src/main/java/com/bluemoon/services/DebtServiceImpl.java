package com.bluemoon.services;

import com.bluemoon.dao.DebtRepository;
import com.bluemoon.models.*;
import java.math.*;
import java.sql.SQLException;
import java.util.*;
import static com.bluemoon.services.DebtResult.Status.*;

public final class DebtServiceImpl implements DebtService {
    private final DebtRepository repository;
    private final AuthorizationService authorization;
    public DebtServiceImpl(DebtRepository repository,AuthorizationService authorization) { this.repository=Objects.requireNonNull(repository); this.authorization=Objects.requireNonNull(authorization); }
    @Override public DebtResult<DebtReport> getDebtByPeriod(DebtQuery input) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        var query=input==null?new DebtQuery(null,null,null,null):input;
        String period=query.kyThu()==null||query.kyThu().isBlank()?null:query.kyThu();
        var errors=new LinkedHashMap<String,String>();
        if(period!=null) { try { BillingPeriod.parseKyThu(period); } catch(IllegalArgumentException invalid) { errors.put("ky_thu","Kỳ thu phải theo định dạng YYYY-MM!"); } }
        if(query.tangTu()!=null&&query.tangTu()<1) { errors.put("tang_tu","Tầng bắt đầu phải từ 1."); }
        if(query.tangDen()!=null&&(query.tangDen()<1||query.tangTu()!=null&&query.tangDen()<query.tangTu())) { errors.put("tang_den","Khoảng tầng không hợp lệ."); }
        if(query.statuses()!=null&&query.statuses().stream().anyMatch(Objects::isNull)) { errors.put("trang_thai","Bộ lọc trạng thái không hợp lệ."); }
        if(!errors.isEmpty()) { return DebtResult.failure(VALIDATION_ERROR,"Dữ liệu lọc không hợp lệ.",errors); }
        Set<ChargeStatus> states=query.statuses()==null?Set.of():Set.copyOf(query.statuses());
        try {
            var data=repository.loadPeriod(period);
            if(data.isEmpty()||data.get().rows().isEmpty()) { return noData(); }
            var all=data.get().rows();
            var visible=all.stream().filter(row -> states.isEmpty()||states.contains(row.trangThai()))
                    .filter(row -> query.tangTu()==null||row.tang()>=query.tangTu()).filter(row -> query.tangDen()==null||row.tang()<=query.tangDen()).toList();
            BigDecimal billed=sum(all,DebtRow::tongTien), paid=sum(all,DebtRow::daThu), debt=sum(all,row -> row.conThieu().max(BigDecimal.ZERO));
            BigDecimal percent=billed.signum()==0?BigDecimal.ZERO.setScale(2):paid.multiply(new BigDecimal("100")).divide(billed,2,RoundingMode.HALF_UP);
            int completed=(int)all.stream().filter(row -> row.trangThai()==ChargeStatus.DA_DONG_DU||row.trangThai()==ChargeStatus.DONG_THUA).count();
            return DebtResult.success(new DebtReport(data.get().kyThu(),visible,billed,paid,debt,percent,completed,all.size(),sum(visible,row -> row.conThieu().max(BigDecimal.ZERO))),
                    visible.isEmpty()?"Không có dữ liệu phù hợp.":"OK");
        } catch(SQLException failure) { return sql(failure); }
    }
    @Override public DebtResult<HouseholdDebt> getHouseholdBalance(String code) {
        authorization.requirePermission(Permission.DEBT_REPORT_VIEW);
        if(code==null||code.isBlank()||code.length()>20) { return DebtResult.failure(VALIDATION_ERROR,"Mã hộ không hợp lệ.",Map.of("ma_ho","Vui lòng nhập mã hộ.")); }
        try { return repository.getHouseholdBalance(code).map(value -> DebtResult.success(value,"OK")).orElseGet(DebtServiceImpl::noData); } catch(SQLException failure) { return sql(failure); }
    }
    private static BigDecimal sum(List<DebtRow> rows,java.util.function.Function<DebtRow,BigDecimal> value) { return rows.stream().map(value).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2); }
    private static <T> DebtResult<T> noData() { return DebtResult.failure(NO_DATA,"Không có dữ liệu phù hợp.",Map.of()); }
    private static <T> DebtResult<T> sql(SQLException failure) {
        return failure.getErrorCode()==1205||failure.getErrorCode()==1213?DebtResult.failure(BUSY,"Dữ liệu đang bận. Vui lòng tải lại.",Map.of()):DebtResult.failure(SYSTEM_ERROR,"Không tải được công nợ. Vui lòng thử lại sau.",Map.of());
    }
}
