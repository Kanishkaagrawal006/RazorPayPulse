package com.razorpay.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_record")
public class SettlementRecord {

    public enum ReconciliationStatus {
        PENDING,
        MATCHED,
        AMBIGUOUS,
        UNRESOLVED
    }

    @Id
    @Column(name = "settlement_id")
    private String settlementId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(name = "tax_on_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxOnFee;

    @Column(name = "reserve_held", nullable = false, precision = 15, scale = 2)
    private BigDecimal reserveHeld = BigDecimal.ZERO;

    @Column(name = "reserve_released", nullable = false, precision = 15, scale = 2)
    private BigDecimal reserveReleased = BigDecimal.ZERO;

    @Column(name = "net_settled", nullable = false, precision = 15, scale = 2)
    private BigDecimal netSettled;

    @Column(name = "utr")
    private String utr;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Version
    @Column(name = "version")
    private Long version;

    protected SettlementRecord() {
    }

    public SettlementRecord(String settlementId, String paymentId, BigDecimal grossAmount,
                            BigDecimal fee, BigDecimal taxOnFee, BigDecimal reserveHeld,
                            BigDecimal reserveReleased, BigDecimal netSettled, String utr,
                            LocalDateTime settledAt) {
        this.settlementId = settlementId;
        this.paymentId = paymentId;
        this.grossAmount = grossAmount;
        this.fee = fee;
        this.taxOnFee = taxOnFee;
        this.reserveHeld = reserveHeld;
        this.reserveReleased = reserveReleased;
        this.netSettled = netSettled;
        this.utr = utr;
        this.settledAt = settledAt;
    }

    public String getSettlementId() { return settlementId; }
    public String getPaymentId() { return paymentId; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getFee() { return fee; }
    public BigDecimal getTaxOnFee() { return taxOnFee; }
    public BigDecimal getReserveHeld() { return reserveHeld; }
    public BigDecimal getReserveReleased() { return reserveReleased; }
    public BigDecimal getNetSettled() { return netSettled; }
    public String getUtr() { return utr; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public Long getVersion() { return version; }

    public void markMatched() {
        this.reconciliationStatus = ReconciliationStatus.MATCHED;
    }

    public void markAmbiguous() {
        this.reconciliationStatus = ReconciliationStatus.AMBIGUOUS;
    }

    public BigDecimal computeExpectedNetSettled() {
        return grossAmount.subtract(fee).subtract(taxOnFee).subtract(reserveHeld).add(reserveReleased);
    }
    public void markUnresolved() {
        this.reconciliationStatus = ReconciliationStatus.UNRESOLVED;
    }
}