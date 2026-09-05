package com.razorpay.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_log")
public class TaxLog {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "gst_on_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal gstOnFee;

    @Column(name = "tds_194o", nullable = false, precision = 15, scale = 2)
    private BigDecimal tds194o;

    @Column(name = "expected_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedDeduction;

    protected TaxLog() {
    }

    public TaxLog(String transactionId, BigDecimal gstOnFee, BigDecimal tds194o, BigDecimal expectedDeduction) {
        this.transactionId = transactionId;
        this.gstOnFee = gstOnFee;
        this.tds194o = tds194o;
        this.expectedDeduction = expectedDeduction;
    }

    public String getTransactionId() { return transactionId; }
    public BigDecimal getGstOnFee() { return gstOnFee; }
    public BigDecimal getTds194o() { return tds194o; }
    public BigDecimal getExpectedDeduction() { return expectedDeduction; }

    public boolean hasVariance() {
        BigDecimal actual = gstOnFee.add(tds194o);
        return actual.compareTo(expectedDeduction) != 0;
    }
}