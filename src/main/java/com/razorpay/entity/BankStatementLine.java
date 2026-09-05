package com.razorpay.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_statement_line")
public class BankStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Integer lineId;

    @Column(name = "narration", nullable = false, columnDefinition = "TEXT")
    private String narration;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "raw_utr_guess")
    private String rawUtrGuess;

    protected BankStatementLine() {
    }

    public BankStatementLine(
            String narration,
            BigDecimal amount,
            LocalDate valueDate,
            String rawUtrGuess) {
        this.narration = narration;
        this.amount = amount;
        this.valueDate = valueDate;
        this.rawUtrGuess = rawUtrGuess;
    }

    public Integer getLineId() {
        return lineId;
    }

    public String getNarration() {
        return narration;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getRawUtrGuess() {
        return rawUtrGuess;
    }
}