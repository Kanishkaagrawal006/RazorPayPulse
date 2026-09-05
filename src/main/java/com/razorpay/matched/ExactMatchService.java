package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExactMatchService {

    public ExactMatchResult findExactCandidate(SettlementRecord settlement,
                                               List<BankStatementLine> availableLines) {
        if (settlement.getUtr() == null) {
            return ExactMatchResult.noCandidate();
        }

        List<BankStatementLine> exactMatches = availableLines.stream()
                .filter(line -> settlement.getUtr().equals(line.getRawUtrGuess()))
                .filter(line -> settlement.getNetSettled().compareTo(line.getAmount()) == 0)
                .toList();

        if (exactMatches.isEmpty()) {
            return ExactMatchResult.noCandidate();
        }
        if (exactMatches.size() > 1) {
            return ExactMatchResult.ambiguous(exactMatches);
        }
        return ExactMatchResult.matched(exactMatches.get(0));
    }

    public List<MatchAttempt> matchBatch(List<SettlementRecord> settlements,
                                         List<BankStatementLine> bankLines) {
        List<SettlementRecord> sorted = settlements.stream()
                .sorted((a, b) -> a.getSettlementId().compareTo(b.getSettlementId()))
                .toList();

        Set<BankStatementLine> consumedLines = new HashSet<>();
        List<MatchAttempt> attempts = new ArrayList<>();

        for (SettlementRecord settlement : sorted) {
            List<BankStatementLine> available = bankLines.stream()
                    .filter(line -> !consumedLines.contains(line))
                    .toList();

            ExactMatchResult result = findExactCandidate(settlement, available);
            if (result.status() == MatchStatus.MATCHED) {
                consumedLines.add(result.matchedLine());
            }
            attempts.add(new MatchAttempt(settlement, result));
        }
        return attempts;
    }
}