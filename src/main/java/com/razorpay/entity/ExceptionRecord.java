package com.razorpay.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exception_record")
public class ExceptionRecord {

    public enum Category {
        Unmatched_Gateway_Record,
        Fee_Variance,
        Timing_Discrepancy,
        Duplicate_Amount_Ambiguity
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "settlement_id")
    private String settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private Category category;

    @Column(name = "reasoning", nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "flagged_at")
    private LocalDateTime flaggedAt;

    protected ExceptionRecord() {
    }

    public ExceptionRecord(String settlementId, Category category, String reasoning) {
        this.settlementId = settlementId;
        this.category = category;
        this.reasoning = reasoning;
        this.flaggedAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public String getSettlementId() { return settlementId; }
    public Category getCategory() { return category; }
    public String getReasoning() { return reasoning; }
    public LocalDateTime getFlaggedAt() { return flaggedAt; }
}