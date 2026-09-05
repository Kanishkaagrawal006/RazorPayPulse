package com.razorpay.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_result")
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "settlement_id")
    private String settlementId;

    @Column(name = "bank_line_id")
    private Integer bankLineId;

    @Column(name = "pass", nullable = false)
    private Short pass;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rules_applied", columnDefinition = "text[]")
    private String[] rulesApplied;

    @Column(name = "reasoning", nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    protected MatchResult() {
    }

    public MatchResult(String settlementId, Integer bankLineId, Short pass,
                       BigDecimal confidence, String[] rulesApplied, String reasoning) {
        this.settlementId = settlementId;
        this.bankLineId = bankLineId;
        this.pass = pass;
        this.confidence = confidence;
        this.rulesApplied = rulesApplied;
        this.reasoning = reasoning;
        this.matchedAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public String getSettlementId() { return settlementId; }
    public Integer getBankLineId() { return bankLineId; }
    public Short getPass() { return pass; }
    public BigDecimal getConfidence() { return confidence; }
    public String[] getRulesApplied() { return rulesApplied; }
    public String getReasoning() { return reasoning; }
    public LocalDateTime getMatchedAt() { return matchedAt; }
}