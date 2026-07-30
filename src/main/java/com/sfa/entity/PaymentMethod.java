package com.sfa.entity;

/** Shared between {@link VendorBillPayment} and {@link InvoicePayment} — one enum, not duplicated per entity. */
public enum PaymentMethod {
    CASH, BANK_TRANSFER, CHEQUE, CARD, OTHER
}
