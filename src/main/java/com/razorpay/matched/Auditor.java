package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.ExceptionRecord;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.rules.ReconciliationRules;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class  Auditor {

    public Optional<ExceptionRecord> checkFeeVariance(SettlementRecord settlement) {
        if (settlement.getGrossAmount().compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal actualRate = settlement.getFee()
                .divide(settlement.getGrossAmount(), 4, RoundingMode.HALF_UP);
        BigDecimal deviation = actualRate.subtract(ReconciliationRules.CONTRACTED_FEE_RATE).abs();

        if (deviation.compareTo(ReconciliationRules.FEE_RATE_TOLERANCE) > 0) {
            String reasoning = String.format(
                    "Fee rate %s%% deviates from the contracted %s%% by %s percentage points (tolerance is %s pp).",
                    toPercent(actualRate), toPercent(ReconciliationRules.CONTRACTED_FEE_RATE),
                    toPercent(deviation), toPercent(ReconciliationRules.FEE_RATE_TOLERANCE));
            return Optional.of(new ExceptionRecord(settlement.getSettlementId(),
                    ExceptionRecord.Category.Fee_Variance, reasoning));
        }
        return Optional.empty();
    }

    public Optional<ExceptionRecord> checkTimingDiscrepancy(SettlementRecord settlement,
                                                            BankStatementLine matchedLine) {
        long gapDays = ChronoUnit.DAYS.between(
                settlement.getSettledAt().toLocalDate(), matchedLine.getValueDate());

        if (gapDays > ReconciliationRules.NORMAL_SETTLEMENT_WINDOW_DAYS) {
            String reasoning = String.format(
                    "Bank credit landed T+%d, exceeding the T+%d tolerance window.",
                    gapDays, ReconciliationRules.NORMAL_SETTLEMENT_WINDOW_DAYS);
            return Optional.of(new ExceptionRecord(settlement.getSettlementId(),
                    ExceptionRecord.Category.Timing_Discrepancy, reasoning));
        }
        return Optional.empty();
    }

    private String toPercent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}