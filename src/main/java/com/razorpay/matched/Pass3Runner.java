// Pass3Runner.java
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
import java.util.Set;

@Component
@Profile("pass3")
@Order(4)
public class Pass3Runner implements CommandLineRunner {

    private final LlmReasoningService llmReasoningService;
    private final MatchPersistenceService persistenceService;
    private final SettlementRecordRepository settlementRepository;
    private final BankStatementLineRepository bankLineRepository;
    private final MatchResultRepository matchResultRepository;

    public Pass3Runner(LlmReasoningService llmReasoningService, MatchPersistenceService persistenceService,
                       SettlementRecordRepository settlementRepository,
                       BankStatementLineRepository bankLineRepository,
                       MatchResultRepository matchResultRepository) {
        this.llmReasoningService = llmReasoningService;
        this.persistenceService = persistenceService;
        this.settlementRepository = settlementRepository;
        this.bankLineRepository = bankLineRepository;
        this.matchResultRepository = matchResultRepository;
    }

    @Override
    public void run(String... args) {
        List<SettlementRecord> pending = settlementRepository
                .findByReconciliationStatus(SettlementRecord.ReconciliationStatus.PENDING);

        Set<Integer> consumedLineIds = Set.copyOf(matchResultRepository.findAllMatchedBankLineIds());
        List<BankStatementLine> available = bankLineRepository.findAll().stream()
                .filter(line -> !consumedLineIds.contains(line.getLineId()))
                .toList();

        System.out.println("Pass 3 starting: " + pending.size() + " pending settlements, "
                + available.size() + " bank lines still available");

        int matched = 0, unresolved = 0, ambiguous = 0;

        for (SettlementRecord settlement : pending) {
            LlmMatchResult result = llmReasoningService.findMatch(settlement, available);

            switch (result.status()) {
                case MATCHED -> {
                    persistenceService.persistMatch(
                            settlement.getSettlementId(),
                            result.matchedLine().getLineId(),
                            (short) 3,
                            BigDecimal.valueOf(result.confidence()).setScale(3, RoundingMode.HALF_UP),
                            new String[]{"LLM_REASONING", "AMOUNT_MATCH", "NARRATION_REFERENCE"},
                            "Gemini reasoning match (confidence=" + result.confidence() + "): " + result.reasoning()
                    );
                    matched++;
                    System.out.println("Pass 3 matched: " + settlement.getSettlementId()
                            + " -> bank line " + result.matchedLine().getLineId()
                            + " (confidence=" + result.confidence() + ")");
                }
                case NO_CANDIDATE -> {
                    unresolved++;
                    persistenceService.persistUnresolved(
                            settlement.getSettlementId(),
                            new ExceptionRecord(settlement.getSettlementId(),
                                    ExceptionRecord.Category.Unmatched_Gateway_Record,
                                    "No sufficiently supported bank match found after Pass 1, Pass 2, and Pass 3.")
                    );
                    System.out.println("Pass 3 unresolved: " + settlement.getSettlementId());
                }
                case AMBIGUOUS -> {
                    ambiguous++;
                    int candidateCount = result.tiedCandidates() == null ? 0 : result.tiedCandidates().size();
                    persistenceService.persistAmbiguous(settlement.getSettlementId(), candidateCount);
                    System.out.println("Pass 3 ambiguous: " + settlement.getSettlementId()
                            + " (" + candidateCount + " candidates)");
                }
            }
        }

        System.out.println("\n=== Pass 3 results ===");
        System.out.println("Matched (Pass 3):  " + matched);
        System.out.println("Unresolved:        " + unresolved);
        System.out.println("Ambiguous:         " + ambiguous);
        System.out.println("Total processed:   " + pending.size());
    }
}