package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.rules.ReconciliationRules;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FuzzyMatchService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[/\\-\\s]+");
    private static final double SCORE_TIE_EPSILON = 1e-9;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    List<String> tokenize(String narration) {
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(narration)) {
            if (!token.isBlank()) {
                tokens.add(token.toUpperCase());
            }
        }
        return tokens;
    }

    public NarrationSimilarity bestMerchantSimilarity(BankStatementLine line) {
        NarrationSimilarity best = NarrationSimilarity.NONE;
        for (String token : tokenize(line.getNarration())) {
            for (String variant : ReconciliationRules.MERCHANT_NAME_VARIANTS) {
                double score = jaroWinkler.apply(token, variant);
                if (score > best.score()) {
                    best = new NarrationSimilarity(score, token, variant);
                }
            }
        }
        return best;
    }

    public FuzzyMatchResult findFuzzyCandidate(SettlementRecord settlement,
                                               List<BankStatementLine> availableLines) {
        List<BankStatementLine> amountAndDateCandidates = availableLines.stream()
                .filter(line -> settlement.getNetSettled().compareTo(line.getAmount()) == 0)
                .filter(line -> withinFuzzyWindow(settlement, line))
                .toList();

        if (amountAndDateCandidates.isEmpty()) {
            return FuzzyMatchResult.noCandidate();
        }

        record ScoredCandidate(BankStatementLine line, NarrationSimilarity similarity) {
        }

        List<ScoredCandidate> scored = amountAndDateCandidates.stream()
                .map(line -> new ScoredCandidate(line, bestMerchantSimilarity(line)))
                .toList();

        double bestScore = scored.stream().mapToDouble(c -> c.similarity().score()).max().orElse(0.0);

        if (bestScore < ReconciliationRules.FUZZY_MATCH_THRESHOLD) {
            return FuzzyMatchResult.noCandidate();
        }

        List<ScoredCandidate> winners = scored.stream()
                .filter(c -> Math.abs(c.similarity().score() - bestScore) < SCORE_TIE_EPSILON)
                .toList();

        if (winners.size() > 1) {
            return FuzzyMatchResult.ambiguous(winners.stream().map(ScoredCandidate::line).toList());
        }

        ScoredCandidate winner = winners.get(0);
        return FuzzyMatchResult.matched(winner.line(), winner.similarity());
    }

    private boolean withinFuzzyWindow(SettlementRecord settlement, BankStatementLine line) {
        long gapDays = ChronoUnit.DAYS.between(settlement.getSettledAt().toLocalDate(), line.getValueDate());
        return gapDays >= 0 && gapDays <= ReconciliationRules.FUZZY_DATE_TOLERANCE_DAYS;
    }

    public List<FuzzyMatchAttempt> matchBatch(List<SettlementRecord> pendingSettlements,
                                              List<BankStatementLine> availableLines) {
        List<SettlementRecord> sorted = pendingSettlements.stream()
                .sorted((a, b) -> a.getSettlementId().compareTo(b.getSettlementId()))
                .toList();

        Set<BankStatementLine> consumedLines = new HashSet<>();
        List<FuzzyMatchAttempt> attempts = new ArrayList<>();

        for (SettlementRecord settlement : sorted) {
            List<BankStatementLine> available = availableLines.stream()
                    .filter(line -> !consumedLines.contains(line))
                    .toList();

            FuzzyMatchResult result = findFuzzyCandidate(settlement, available);
            if (result.status() == MatchStatus.MATCHED) {
                consumedLines.add(result.matchedLine());
            }
            attempts.add(new FuzzyMatchAttempt(settlement, result));
        }
        return attempts;
    }
}