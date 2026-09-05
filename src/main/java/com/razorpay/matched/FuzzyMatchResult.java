// FuzzyMatchResult.java
package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import java.util.List;

public record FuzzyMatchResult(
        MatchStatus status,
        BankStatementLine matchedLine,
        NarrationSimilarity similarity,
        List<BankStatementLine> tiedCandidates
) {
    public static FuzzyMatchResult matched(BankStatementLine line, NarrationSimilarity similarity) {
        return new FuzzyMatchResult(MatchStatus.MATCHED, line, similarity, List.of());
    }
    public static FuzzyMatchResult noCandidate() {
        return new FuzzyMatchResult(MatchStatus.NO_CANDIDATE, null, null, List.of());
    }
    public static FuzzyMatchResult ambiguous(List<BankStatementLine> tied) {
        return new FuzzyMatchResult(MatchStatus.AMBIGUOUS, null, null, tied);
    }
}