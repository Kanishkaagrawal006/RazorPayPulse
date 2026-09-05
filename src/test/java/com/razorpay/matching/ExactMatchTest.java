package com.razorpay.matching;

import com.razorpay.dataseed.GeneratedRecord;
import com.razorpay.dataseed.SyntheticDataGenerator;
import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.matched.ExactMatchResult;
import com.razorpay.matched.ExactMatchService;
import com.razorpay.matched.MatchAttempt;
import com.razorpay.matched.MatchStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExactMatchServiceTest {

    private final ExactMatchService service = new ExactMatchService();
    private final SyntheticDataGenerator generator = new SyntheticDataGenerator();

    @Test
    void exactUtrAndAmountMatch_isMatched() {
        SettlementRecord settlement = settlement("stl_1", "UTR111", "1000.00");
        BankStatementLine line = bankLine(1, "UTR111", "1000.00");
        ExactMatchResult result = service.findExactCandidate(settlement, List.of(line));
        assertEquals(MatchStatus.MATCHED, result.status());
        assertSame(line, result.matchedLine());
    }

    @Test
    void utrMatchesButAmountDiffersByOnePaise_isNotMatched() {
        SettlementRecord settlement = settlement("stl_1", "UTR111", "1000.00");
        BankStatementLine line = bankLine(1, "UTR111", "1000.01");
        ExactMatchResult result = service.findExactCandidate(settlement, List.of(line));
        assertEquals(MatchStatus.NO_CANDIDATE, result.status());
    }

    @Test
    void differentScaleButNumericallyEqualAmounts_stillMatch() {
        SettlementRecord settlement = settlement("stl_1", "UTR111", "1000.50");
        BankStatementLine line = bankLine(1, "UTR111", "1000.5000");
        ExactMatchResult result = service.findExactCandidate(settlement, List.of(line));
        assertEquals(MatchStatus.MATCHED, result.status());
    }

    @Test
    void nullUtrSettlement_neverMatchesEvenWithCorrectAmount() {
        SettlementRecord settlement = settlement("stl_1", null, "1000.00");
        BankStatementLine line = bankLine(1, null, "1000.00");
        ExactMatchResult result = service.findExactCandidate(settlement, List.of(line));
        assertEquals(MatchStatus.NO_CANDIDATE, result.status());
    }

    @Test
    void twoLinesWithSameUtrAndAmount_isAmbiguousNotArbitrarilyPicked() {
        SettlementRecord settlement = settlement("stl_1", "UTR111", "1000.00");
        BankStatementLine line1 = bankLine(1, "UTR111", "1000.00");
        BankStatementLine line2 = bankLine(2, "UTR111", "1000.00");
        ExactMatchResult result = service.findExactCandidate(settlement, List.of(line1, line2));
        assertEquals(MatchStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.tiedCandidates().size());
        assertNull(result.matchedLine());
    }

    @Test
    void bankLineAlreadyConsumedByAnotherSettlement_isNotReusedInBatch() {
        SettlementRecord settlementA = settlement("stl_a", "UTR111", "1000.00");
        SettlementRecord settlementB = settlement("stl_b", "UTR222", "1000.00");
        BankStatementLine sharedAmountLine = bankLine(1, "UTR111", "1000.00");
        List<MatchAttempt> attempts = service.matchBatch(
                List.of(settlementA, settlementB), List.of(sharedAmountLine));
        MatchAttempt attemptA = attempts.stream().filter(a -> a.settlement() == settlementA).findFirst().orElseThrow();
        MatchAttempt attemptB = attempts.stream().filter(a -> a.settlement() == settlementB).findFirst().orElseThrow();
        assertEquals(MatchStatus.MATCHED, attemptA.result().status());
        assertEquals(MatchStatus.NO_CANDIDATE, attemptB.result().status());
    }

    @Test
    void allFortyFourCleanRecordsMatchInPass1() {
        List<GeneratedRecord> batch = generator.generate(42L);
        List<SettlementRecord> settlements = extractSettlements(batch);
        List<BankStatementLine> bankLines = extractBankLines(batch);
        List<MatchAttempt> attempts = service.matchBatch(settlements, bankLines);
        long matchedCount = attempts.stream().filter(a -> a.result().status() == MatchStatus.MATCHED).count();
        assertEquals(46, matchedCount);
    }

    @Test
    void garbledUtrAndLlmReasoningRecords_correctlyLeftForLaterPasses() {
        List<GeneratedRecord> batch = generator.generate(42L);
        List<SettlementRecord> settlements = extractSettlements(batch);
        List<BankStatementLine> bankLines = extractBankLines(batch);
        List<MatchAttempt> attempts = service.matchBatch(settlements, bankLines);
        long noCandidateCount = attempts.stream().filter(a -> a.result().status() == MatchStatus.NO_CANDIDATE).count();
        assertEquals(6, noCandidateCount);
    }

    @Test
    void duplicateAmountPair_isAmbiguousInPass1() {
        List<GeneratedRecord> batch = generator.generate(42L);
        List<SettlementRecord> settlements = extractSettlements(batch);
        List<BankStatementLine> bankLines = extractBankLines(batch);
        List<MatchAttempt> attempts = service.matchBatch(settlements, bankLines);
        long dupNoCandidateCount = attempts.stream()
                .filter(a -> a.settlement().getSettlementId().startsWith("stl_dup"))
                .filter(a -> a.result().status() == MatchStatus.NO_CANDIDATE)
                .count();
        assertEquals(2, dupNoCandidateCount);
    }

    private List<SettlementRecord> extractSettlements(List<GeneratedRecord> batch) {
        return batch.stream().map(GeneratedRecord::settlement).toList();
    }

    private List<BankStatementLine> extractBankLines(List<GeneratedRecord> batch) {
        return batch.stream().map(GeneratedRecord::bankLine).filter(line -> line != null).toList();
    }

    private SettlementRecord settlement(String id, String utr, String netSettled) {
        return new SettlementRecord(id, "pay_" + id, new BigDecimal(netSettled),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(netSettled), utr, LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    private BankStatementLine bankLine(int id, String utr, String amount) {
        return new BankStatementLine("narration", new BigDecimal(amount), LocalDate.of(2026, 8, 2), utr);
    }
}