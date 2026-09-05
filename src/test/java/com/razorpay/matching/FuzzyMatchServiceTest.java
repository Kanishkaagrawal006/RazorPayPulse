package com.razorpay.matching;

import com.razorpay.dataseed.GeneratedRecord;
import com.razorpay.dataseed.SyntheticDataGenerator;
import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.matched.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzyMatchServiceTest {

    private final FuzzyMatchService service = new FuzzyMatchService();
    private final SyntheticDataGenerator generator = new SyntheticDataGenerator();

    @Test
    void garbledUtrNarration_scoresAboveThreshold_asMeasured() {
        BankStatementLine line = new BankStatementLine(
                "UTIB0000123/RAZOPAY/REF6789", new BigDecimal("980.00"),
                LocalDate.of(2026, 8, 2), null);
        NarrationSimilarity result = service.bestMerchantSimilarity(line);
        assertEquals(0.975, result.score(), 0.001);
        assertEquals("RAZOPAY", result.matchedToken());
    }

    @Test
    void needsLlmReasoningNarration_scoresBelowThreshold_asMeasured() {
        BankStatementLine line = new BankStatementLine(
                "NEFT-XXXXX-RZRSFTWRE PVT-0047", new BigDecimal("980.00"),
                LocalDate.of(2026, 8, 2), null);
        NarrationSimilarity result = service.bestMerchantSimilarity(line);
        assertEquals(0.806, result.score(), 0.001);
        assertTrue(result.score() < 0.85);
    }

    @Test
    void wholeStringComparison_wouldHaveFailed_thisIsWhyWeTokenize() {
        double wholeStringScore = new org.apache.commons.text.similarity.JaroWinklerSimilarity()
                .apply("RAZORPAY SOFTWARE PVT/UTR123456789/SETTLEMENT", "UTIB0000123/RAZOPAY/REF6789");
        assertTrue(wholeStringScore < 0.85);
    }

    @Test
    void wrongAmount_excludedEvenWithPerfectNarrationMatch() {
        SettlementRecord settlement = settlement("stl_1", "1000.00", LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine wrongAmountLine = bankLine("RAZORPAY SOFTWARE", "999.00", LocalDate.of(2026, 8, 2));
        FuzzyMatchResult result = service.findFuzzyCandidate(settlement, List.of(wrongAmountLine));
        assertEquals(MatchStatus.NO_CANDIDATE, result.status());
    }

    @Test
    void dateBeyondFuzzyWindow_excludedEvenWithCorrectAmount() {
        SettlementRecord settlement = settlement("stl_1", "1000.00", LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine tooLateLine = bankLine("RAZORPAY SOFTWARE", "1000.00", LocalDate.of(2026, 8, 10));
        FuzzyMatchResult result = service.findFuzzyCandidate(settlement, List.of(tooLateLine));
        assertEquals(MatchStatus.NO_CANDIDATE, result.status());
    }

    @Test
    void correctAmountAndDate_butLowNarrationScore_fallsThroughToNoCandidate() {
        SettlementRecord settlement = settlement("stl_1", "1000.00", LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine unrelatedNarrationLine = bankLine("XYZ CORP TRANSFER", "1000.00", LocalDate.of(2026, 8, 2));
        FuzzyMatchResult result = service.findFuzzyCandidate(settlement, List.of(unrelatedNarrationLine));
        assertEquals(MatchStatus.NO_CANDIDATE, result.status());
    }

    @Test
    void twoIdenticalCandidates_isAmbiguousNotArbitrarilyPicked() {
        SettlementRecord settlement = settlement("stl_dup_1", "1000.00", LocalDateTime.of(2026, 8, 1, 10, 0));
        BankStatementLine line1 = bankLine("RAZORPAY SETTLEMENT", "1000.00", LocalDate.of(2026, 8, 2));
        BankStatementLine line2 = bankLine("RAZORPAY SETTLEMENT", "1000.00", LocalDate.of(2026, 8, 2));
        FuzzyMatchResult result = service.findFuzzyCandidate(settlement, List.of(line1, line2));
        assertEquals(MatchStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.tiedCandidates().size());
        assertNull(result.matchedLine());
    }

    @Test
    void fullPipeline_garbledUtrRecordsResolveInPass2() {
        PipelineResult pipeline = runFullPipeline();
        long pass2Matched = pipeline.pass2Attempts.stream()
                .filter(a -> a.result().status() == MatchStatus.MATCHED)
                .filter(a -> a.settlement().getSettlementId().equals("stl_0045")
                        || a.settlement().getSettlementId().equals("stl_0046"))
                .count();
        assertEquals(2, pass2Matched);
    }

    @Test
    void fullPipeline_needsLlmReasoningRecordStaysUnresolvedAfterPass2() {
        PipelineResult pipeline = runFullPipeline();
        FuzzyMatchAttempt attempt = pipeline.pass2Attempts.stream()
                .filter(a -> a.settlement().getSettlementId().equals("stl_0047"))
                .findFirst().orElseThrow();
        assertEquals(MatchStatus.NO_CANDIDATE, attempt.result().status());
    }

    @Test
    void fullPipeline_duplicateAmountPairBecomesGenuinelyAmbiguousInPass2() {
        PipelineResult pipeline = runFullPipeline();
        long ambiguousCount = pipeline.pass2Attempts.stream()
                .filter(a -> a.settlement().getSettlementId().startsWith("stl_dup"))
                .filter(a -> a.result().status() == MatchStatus.AMBIGUOUS)
                .count();
        assertEquals(2, ambiguousCount);
    }

    @Test
    void fullPipeline_chargebackStillUnresolvedAfterPass2_noBankLineExistsAtAll() {
        PipelineResult pipeline = runFullPipeline();
        FuzzyMatchAttempt attempt = pipeline.pass2Attempts.stream()
                .filter(a -> a.settlement().getSettlementId().startsWith("stl_chargeback"))
                .findFirst().orElseThrow();
        assertEquals(MatchStatus.NO_CANDIDATE, attempt.result().status());
    }

    private record PipelineResult(List<FuzzyMatchAttempt> pass2Attempts) {
    }

    private PipelineResult runFullPipeline() {
        List<GeneratedRecord> batch = generator.generate(42L);
        List<SettlementRecord> allSettlements = batch.stream().map(GeneratedRecord::settlement).toList();
        List<BankStatementLine> allBankLines = batch.stream()
                .map(GeneratedRecord::bankLine).filter(l -> l != null).toList();

        ExactMatchService pass1 = new ExactMatchService();
        List<MatchAttempt> pass1Attempts = pass1.matchBatch(allSettlements, allBankLines);

        List<SettlementRecord> stillPending = pass1Attempts.stream()
                .filter(a -> a.result().status() == MatchStatus.NO_CANDIDATE)
                .map(MatchAttempt::settlement)
                .toList();

        List<BankStatementLine> consumedByPass1 = pass1Attempts.stream()
                .filter(a -> a.result().status() == MatchStatus.MATCHED)
                .map(a -> a.result().matchedLine())
                .toList();
        List<BankStatementLine> stillAvailable = allBankLines.stream()
                .filter(line -> !consumedByPass1.contains(line))
                .toList();

        List<FuzzyMatchAttempt> pass2Attempts = service.matchBatch(stillPending, stillAvailable);
        return new PipelineResult(pass2Attempts);
    }

    private SettlementRecord settlement(String id, String netSettled, LocalDateTime settledAt) {
        return new SettlementRecord(id, "pay_" + id, new BigDecimal(netSettled),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(netSettled), null, settledAt);
    }

    private BankStatementLine bankLine(String narration, String amount, LocalDate valueDate) {
        return new BankStatementLine(narration, new BigDecimal(amount), valueDate, null);
    }
}