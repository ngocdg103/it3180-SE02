package com.bluemoon.services;

import com.bluemoon.models.*;
import java.util.*;

/** Blocking API. Record requires PAYMENT_RECORD (ACCOUNTANT only); all reads PAYMENT_HISTORY_VIEW_ALL. */
public interface ReceiptService {
    ReceiptResult<List<PaymentInvoice>> listOutstanding();
    ReceiptResult<List<Receipt>> listByHousehold(String maHo);
    ReceiptResult<Optional<Receipt>> findByRequestId(String requestId);
    ReceiptResult<ReceiptOutcome> recordPayment(ReceiptForm form);
}
