package com.church.operation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("tax_receipt_counters")
public class TaxReceiptCounter {
    @Id
    private int taxYear;
    private long nextSequence;

    public int getTaxYear() { return taxYear; }
    public void setTaxYear(int taxYear) { this.taxYear = taxYear; }
    public long getNextSequence() { return nextSequence; }
    public void setNextSequence(long nextSequence) { this.nextSequence = nextSequence; }
}
