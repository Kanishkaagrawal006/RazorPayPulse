package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.ExceptionRecord;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.repository.BankStatementLineRepository;
import com.razorpay.repository.ExceptionRecordRepository;
import com.razorpay.repository.MatchResultRepository;
import com.razorpay.repository.SettlementRecordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@Profile("pass1")
@Order(2)
public class Pass1Runner implements CommandLineRunner {

    private final ExactMatchService exactMatchService;
    private final Auditor auditor;
    private final MatchPersistenceService persistenceService;
    private final SettlementRecordRepository settlementRepository;
    private final BankStatementLineRepository bankLineRepository;
    private final MatchResultRepository matchResultRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;

    public Pass1Runner(
            ExactMatchService exactMatchService,
            Auditor auditor,
            MatchPersistenceService persistenceService,
            SettlementRecordRepository settlementRepository,
            BankStatementLineRepository bankLineRepository, MatchResultRepository matchResultRepository, ExceptionRecordRepository exceptionRecordRepository) {

        this.exactMatchService = exactMatchService;
        this.auditor = auditor;
        this.persistenceService = persistenceService;
        this.settlementRepository = settlementRepository;
        this.bankLineRepository = bankLineRepository;
        this.matchResultRepository = matchResultRepository;
        this.exceptionRecordRepository = exceptionRecordRepository;
    }

    @Override
    public void run(String... args) {
        List<SettlementRecord> settlements =
                settlementRepository.findAll();

        List<BankStatementLine> bankLines =
                bankLineRepository.findAll();

        System.out.println(
                "Pass 1 starting: "
                        + settlements.size()
                        + " settlements, "
                        + bankLines.size()
                        + " bank lines available"
        );

        List<MatchAttempt> attempts =
                exactMatchService.matchBatch(
                        settlements,
                        bankLines
                );

        int matched = 0;
        int noCandidate = 0;
        int ambiguous = 0;
        int feeFlags = 0;
        int timingFlags = 0;
        for (MatchAttempt attempt : attempts) {

            SettlementRecord settlement =
                    attempt.settlement();

            ExactMatchResult result =
                    attempt.result();

            switch (result.status()) {

                case MATCHED -> {

                    persistenceService.persistMatch(
                            settlement.getSettlementId(),

                            result.matchedLine().getLineId(),

                            (short) 1,

                            new BigDecimal("1.000"),

                            new String[]{
                                    "EXACT_UTR_MATCH",
                                    "EXACT_AMOUNT_MATCH"
                            },

                            "Exact match: UTR and amount both matched "
                                    + "exactly one unambiguous bank line."
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
        System.out.println("\n=== Pass 1 results ===");

        System.out.println(
                "Matched (Pass 1):                       "
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
                "No candidate (-> Pass 2/3 next):        "
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
        System.out.println(
                "MatchResult rows currently in DB: "
                        + matchResultRepository.count()
        );

        System.out.println(
                "ExceptionRecord rows currently in DB: "
                        + exceptionRecordRepository.count()
        );
    }
}