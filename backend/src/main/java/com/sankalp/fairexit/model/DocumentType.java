package com.sankalp.fairexit.model;

public enum DocumentType {
    AGREEMENT,
    /** A photo or PDF of the landlord's deduction bill. */
    BILL,
    PHOTO,
    /** Proof of a payment, for example the deposit transfer. */
    RECEIPT,
    /** An electricity, water or gas bill; its amount feeds audit rule 1. */
    UTILITY_BILL
}
