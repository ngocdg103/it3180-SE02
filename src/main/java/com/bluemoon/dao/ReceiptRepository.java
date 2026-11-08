package com.bluemoon.dao;

import com.bluemoon.models.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository {
    ReceiptOutcome record(Receipt receipt) throws SQLException;
    List<PaymentInvoice> listOutstanding() throws SQLException;
    List<Receipt> listByHousehold(String maHo) throws SQLException;
    Optional<Receipt> findByRequestId(String requestId) throws SQLException;
}
