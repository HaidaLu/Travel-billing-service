package com.navan.billing.transaction.service;

/**
 * "Is this transaction on any invoice?" Declared here, implemented by the invoice persistence layer,
 * so the transaction module doesn't depend on the invoice module (invoice → transaction only).
 */
public interface InvoiceReferenceChecker {

    boolean isInvoiced(long transactionId);
}
