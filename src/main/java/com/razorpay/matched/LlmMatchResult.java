package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;

import java.util.List;

public record LlmMatchResult(
        MatchStatus status,
        BankStatementLine matchedLine,
        double confidence,
        String reasoning,
        List<BankStatementLine> tiedCandidates
) {
    public static LlmMatchResult matched(BankStatementLine line, double confidence, String reasoning) {
        return new LlmMatchResult(MatchStatus.MATCHED, line, confidence, reasoning, List.of());
    }
    public static LlmMatchResult noCandidate() {
        return new LlmMatchResult(MatchStatus.NO_CANDIDATE, null, 0.0, null, List.of());
    }
    public static LlmMatchResult ambiguous(List<BankStatementLine> candidates) {
        return new LlmMatchResult(MatchStatus.AMBIGUOUS, null, 0.0, null, candidates);
    }
}