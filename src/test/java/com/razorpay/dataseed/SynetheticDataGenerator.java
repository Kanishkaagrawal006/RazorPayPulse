package com.razorpay.dataseed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class SyntheticDataGeneratorTest {

    private final SyntheticDataGenerator generator = new SyntheticDataGenerator();

    @Test
    void batchHasExactly52Records() {
        List<GeneratedRecord> batch = generator.generate(42L);
        assertEquals(52, batch.size());
    }

    @Test
    void batchCompositionMatchesTheSeededEdgeCasePlan() {
        List<GeneratedRecord> batch = generator.generate(42L);
        Map<ExpectedOutcome, Long> counts = batch.stream()
                .collect(Collectors.groupingBy(GeneratedRecord::expectedOutcome, Collectors.counting()));

        assertEquals(44L, counts.get(ExpectedOutcome.MATCHED_PASS_1));
        assertEquals(2L, counts.get(ExpectedOutcome.MATCHED_PASS_2));
        assertEquals(1L, counts.get(ExpectedOutcome.MATCHED_PASS_3));
        assertEquals(1L, counts.get(ExpectedOutcome.EXCEPTION_FEE_VARIANCE));
        assertEquals(1L, counts.get(ExpectedOutcome.EXCEPTION_TIMING_DISCREPANCY));
        assertEquals(1L, counts.get(ExpectedOutcome.EXCEPTION_UNMATCHED_GATEWAY_RECORD));
        assertEquals(2L, counts.get(ExpectedOutcome.EXCEPTION_DUPLICATE_AMOUNT_AMBIGUITY));
    }

    @Test
    void everySettlementsNetSettledIsInternallyConsistent() {
        List<GeneratedRecord> batch = generator.generate(42L);
        for (GeneratedRecord record : batch) {
            BigDecimal expected = record.settlement().computeExpectedNetSettled();
            BigDecimal actual = record.settlement().getNetSettled();
            assertEquals(0, expected.compareTo(actual),
                    "Inconsistent net_settled for " + record.settlement().getSettlementId());
        }
    }

    @Test
    void feeVarianceRecordIsActuallyOutsideTolerance() {
        List<GeneratedRecord> batch = generator.generate(42L);
        GeneratedRecord feeVarianceRecord = batch.stream()
                .filter(r -> r.expectedOutcome() == ExpectedOutcome.EXCEPTION_FEE_VARIANCE)
                .findFirst().orElseThrow();

        BigDecimal gross = feeVarianceRecord.settlement().getGrossAmount();
        BigDecimal fee = feeVarianceRecord.settlement().getFee();
        BigDecimal actualRate = fee.divide(gross, 4, java.math.RoundingMode.HALF_UP);
        BigDecimal deviation = actualRate.subtract(SyntheticDataGenerator.CONTRACTED_FEE_RATE).abs();

        assertTrue(deviation.compareTo(SyntheticDataGenerator.FEE_RATE_TOLERANCE) > 0);
    }

    @Test
    void timingDiscrepancyRecordActuallyExceedsTheWindow() {
        List<GeneratedRecord> batch = generator.generate(42L);
        GeneratedRecord timingRecord = batch.stream()
                .filter(r -> r.expectedOutcome() == ExpectedOutcome.EXCEPTION_TIMING_DISCREPANCY)
                .findFirst().orElseThrow();

        long gapDays = java.time.temporal.ChronoUnit.DAYS.between(
                timingRecord.settlement().getSettledAt().toLocalDate(),
                timingRecord.bankLine().getValueDate());

        assertTrue(gapDays > SyntheticDataGenerator.NORMAL_SETTLEMENT_WINDOW_DAYS);
    }

    @Test
    void missingReferenceChargebackHasNoBankLine() {
        List<GeneratedRecord> batch = generator.generate(42L);
        GeneratedRecord chargeback = batch.stream()
                .filter(r -> r.expectedOutcome() == ExpectedOutcome.EXCEPTION_UNMATCHED_GATEWAY_RECORD)
                .findFirst().orElseThrow();

        assertNull(chargeback.bankLine());
        assertNull(chargeback.settlement().getUtr());
    }

    @Test
    void duplicateAmountPairIsGenuinelyIndistinguishable() {
        List<GeneratedRecord> batch = generator.generate(42L);
        List<GeneratedRecord> duplicates = batch.stream()
                .filter(r -> r.expectedOutcome() == ExpectedOutcome.EXCEPTION_DUPLICATE_AMOUNT_AMBIGUITY)
                .toList();

        assertEquals(2, duplicates.size());
        GeneratedRecord a = duplicates.get(0);
        GeneratedRecord b = duplicates.get(1);

        assertEquals(0, a.settlement().getNetSettled().compareTo(b.settlement().getNetSettled()));
        assertEquals(a.settlement().getSettledAt().toLocalDate(), b.settlement().getSettledAt().toLocalDate());
        assertNull(a.settlement().getUtr());
        assertNull(b.settlement().getUtr());
        assertEquals(a.bankLine().getNarration(), b.bankLine().getNarration());
    }

    @Test
    void sameSeedProducesByteIdenticalBatchTwice() {
        List<GeneratedRecord> first = generator.generate(42L);
        List<GeneratedRecord> second = generator.generate(42L);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            SettlementRecordSnapshot s1 = SettlementRecordSnapshot.of(first.get(i));
            SettlementRecordSnapshot s2 = SettlementRecordSnapshot.of(second.get(i));
            assertEquals(s1, s2, "Record " + i + " differs between two runs with the same seed");
        }
    }

    private record SettlementRecordSnapshot(String settlementId, BigDecimal netSettled, String utr,
                                            java.time.LocalDateTime settledAt) {
        static SettlementRecordSnapshot of(GeneratedRecord r) {
            return new SettlementRecordSnapshot(
                    r.settlement().getSettlementId(), r.settlement().getNetSettled(),
                    r.settlement().getUtr(), r.settlement().getSettledAt());
        }
    }
}