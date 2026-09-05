package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.ExceptionRecord;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.repository.BankStatementLineRepository;
import com.razorpay.repository.MatchResultRepository;
import com.razorpay.repository.SettlementRecordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("pass2")
@Order(3)
public class Pass2Runner implements CommandLineRunner {

    private final FuzzyMatchService fuzzyMatchService;
    private final Auditor auditor;
    private final MatchPersistenceService persistenceService;
    private final SettlementRecordRepository settlementRepository;
    private final BankStatementLineRepository bankLineRepository;
    private final MatchResultRepository matchResultRepository;

    public Pass2Runner(
            FuzzyMatchService fuzzyMatchService,
            Auditor auditor,
            MatchPersistenceService persistenceService,
            SettlementRecordRepository settlementRepository,
            BankStatementLineRepository bankLineRepository,
            MatchResultRepository matchResultRepository) {

        this.fuzzyMatchService = fuzzyMatchService;
        this.auditor = auditor;
        this.persistenceService = persistenceService;
        this.settlementRepository = settlementRepository;
        this.bankLineRepository = bankLineRepository;
        this.matchResultRepository = matchResultRepository;
    }

    @Override
    public void run(String... args) {
        List<SettlementRecord> pending =
                settlementRepository.findByReconciliationStatus(
                        SettlementRecord.ReconciliationStatus.PENDING
                );
        Set<Integer> consumedLineIds =
                Set.copyOf(
                        matchResultRepository.findAllMatchedBankLineIds()
                );

        List<BankStatementLine> available =
                bankLineRepository.findAll()
                        .stream()
                        .filter(line ->
                                !consumedLineIds.contains(
                                        line.getLineId()
                                )
                        )
                        .toList();

        System.out.println(
                "Pass 2 starting: "
                        + pending.size()
                        + " pending settlements, "
                        + available.size()
                        + " bank lines still available"
        );
        List<FuzzyMatchAttempt> attempts =
                fuzzyMatchService.matchBatch(
                        pending,
                        available
                );

        int matched = 0;
        int noCandidate = 0;
        int ambiguous = 0;
        int feeFlags = 0;
        int timingFlags = 0;

        for (FuzzyMatchAttempt attempt : attempts) {

            SettlementRecord settlement =
                    attempt.settlement();

            FuzzyMatchResult result =
                    attempt.result();

            switch (result.status()) {
                case MATCHED -> {

                    NarrationSimilarity sim =
                            result.similarity();

                    String reasoning = String.format(
                            "Fuzzy match: exact amount + date within tolerance. "
                                    + "Narration token '%s' scored %.3f "
                                    + "against merchant variant '%s' "
                                    + "(threshold 0.85).",

                            sim.matchedToken(),
                            sim.score(),
                            sim.matchedVariant()
                    );
                    persistenceService.persistMatch(
                            settlement.getSettlementId(),

                            result.matchedLine().getLineId(),

                            (short) 2,

                            BigDecimal.valueOf(sim.score())
                                    .setScale(
                                            3,
                                            RoundingMode.HALF_UP
                                    ),

                            new String[]{
                                    "AMOUNT_MATCH",
                                    "DATE_WINDOW_WIDE",
                                    "NARRATION_SIMILARITY"
                            },

                            reasoning
                    );

                    matched++;

                    Optional<ExceptionRecord> feeIssue =
                            auditor.checkFeeVariance(
                                    settlement
                            );

                    if (feeIssue.isPresent()) {

                        persistenceService.recordAuditException(
                                feeIssue.get()
                        );

                        feeFlags++;
                    }

                    Optional<ExceptionRecord> timingIssue =
                            auditor.checkTimingDiscrepancy(
                                    settlement,
                                    result.matchedLine()
                            );

                    if (timingIssue.isPresent()) {

                        persistenceService.recordAuditException(
                                timingIssue.get()
                        );

                        timingFlags++;
                    }
                }
                case NO_CANDIDATE -> {

                    noCandidate++;
                }
                case AMBIGUOUS -> {

                    ambiguous++;

                    persistenceService.persistAmbiguous(
                            settlement.getSettlementId(),
                            result.tiedCandidates().size()
                    );
                }
            }
        }

        /*
         * ---------------------------------------------------------
         * 6. Print Pass 2 results.
         * ---------------------------------------------------------
         */
        System.out.println("\n=== Pass 2 results ===");

        System.out.println(
                "Matched (Pass 2):                       "
                        + matched
        );

        System.out.println(
                "  - of which Fee_Variance flagged:      "
                        + feeFlags
        );

        System.out.println(
                "  - of which Timing_Discrepancy flagged: "
                        + timingFlags
        );

        System.out.println(
                "No candidate (-> Pass 3 next):           "
                        + noCandidate
        );

        System.out.println(
                "Ambiguous (Duplicate_Amount_Ambiguity):  "
                        + ambiguous
        );

        System.out.println(
                "Total processed:                        "
                        + attempts.size()
        );
    }
}