package com.razorpay.dataseed;


import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.entity.TaxLog;

public record GeneratedRecord(
        SettlementRecord settlement,
        BankStatementLine bankLine,
        TaxLog taxLog,
        ExpectedOutcome expectedOutcome,
        String note
) {
}