package com.razorpay.matching;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.ExceptionRecord;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.matched.Auditor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationAuditorTest {

    private final Auditor auditor = new Auditor();

    @Test
    void feeExactlyAtContractedRate_noException() {
        SettlementRecord settlement = settlementWithFee("1000.00", "20.00");
        assertTrue(auditor.checkFeeVariance(settlement).isEmpty());
    }

    @Test
    void feeAtEdgeOfTolerance_noException() {
        SettlementRecord settlement = settlementWithFee("10000.00", "210.00");
        assertTrue(auditor.checkFeeVariance(settlement).isEmpty());
    }

    @Test
    void feeJustOverTolerance_isFlagged() {
        SettlementRecord settlement = settlementWithFee("10000.00", "211.00");
        Optional<ExceptionRecord> result = auditor.checkFeeVariance(settlement);
        assertTrue(result.isPresent());
        assertEquals(ExceptionRecord.Category.Fee_Variance, result.get().getCategory());
    }

    @Test
    void feeAt2Point3Percent_isFlaggedLikeTheSeededCase() {
        SettlementRecord settlement = settlementWithFee("10000.00", "230.00");
        Optional<ExceptionRecord> result = auditor.checkFeeVariance(settlement);
        assertTrue(result.isPresent());
        assertTrue(result.get().getReasoning().contains("2.30"));
    }

    @Test
    void zeroGrossAmount_doesNotDivideByZero() {
        SettlementRecord settlement = settlementWithFee("0.00", "0.00");
        assertDoesNotThrow(() -> auditor.checkFeeVariance(settlement));
    }

    @Test
    void settlementCreditedNextDay_withinWindow_noException() {
        SettlementRecord settlement = settlementWithDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine line = bankLineWithDate(LocalDate.of(2026, 8, 2));
        assertTrue(auditor.checkTimingDiscrepancy(settlement, line).isEmpty());
    }

    @Test
    void settlementCreditedExactlyAtWindowBoundary_noException() {
        SettlementRecord settlement = settlementWithDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine line = bankLineWithDate(LocalDate.of(2026, 8, 3));
        assertTrue(auditor.checkTimingDiscrepancy(settlement, line).isEmpty());
    }

    @Test
    void settlementCreditedOneDayPastWindow_isFlagged() {
        SettlementRecord settlement = settlementWithDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine line = bankLineWithDate(LocalDate.of(2026, 8, 4));
        Optional<ExceptionRecord> result = auditor.checkTimingDiscrepancy(settlement, line);
        assertTrue(result.isPresent());
        assertEquals(ExceptionRecord.Category.Timing_Discrepancy, result.get().getCategory());
    }

    @Test
    void settlementCreditedFourDaysLate_matchesTheSeededCase() {
        SettlementRecord settlement = settlementWithDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine line = bankLineWithDate(LocalDate.of(2026, 8, 5));
        Optional<ExceptionRecord> result = auditor.checkTimingDiscrepancy(settlement, line);
        assertTrue(result.isPresent());
        assertTrue(result.get().getReasoning().contains("T+4"));
    }

    private SettlementRecord settlementWithFee(String gross, String fee) {
        return new SettlementRecord("stl_test", "pay_test", new BigDecimal(gross), new BigDecimal(fee),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(gross).subtract(new BigDecimal(fee)),
                "UTR000000001", LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    private SettlementRecord settlementWithDate(LocalDateTime settledAt) {
        return new SettlementRecord("stl_test", "pay_test", new BigDecimal("1000.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("980.00"),
                "UTR000000001", settledAt);
    }

    private BankStatementLine bankLineWithDate(LocalDate valueDate) {
        return new BankStatementLine("narration", new BigDecimal("980.00"), valueDate, "UTR000000001");
    }
}