package com.navan.billing.invoice.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.billing.common.id.PublicId;
import com.navan.billing.invoice.dto.FeeRollupLine;
import com.navan.billing.invoice.dto.Invoice;
import com.navan.billing.invoice.dto.TaxRollupLine;
import com.navan.billing.invoice.dto.TransactionSnapshot;

public record InvoiceResponse(
        String id,
        String currency,
        BigDecimal total,
        List<TransactionSnapshot> lines,
        List<TaxRollupLine> taxRollup,
        List<FeeRollupLine> feeRollup,
        Instant createdAt,
        Instant updatedAt) {

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(PublicId.INVOICE.encode(i.id()), i.currency(), i.total(), i.lines(),
                i.taxRollup(), i.feeRollup(), i.createdAt(), i.updatedAt());
    }
}
