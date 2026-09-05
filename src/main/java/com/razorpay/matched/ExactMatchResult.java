package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;

import java.util.List;

public record ExactMatchResult(
        MatchStatus status,
        BankStatementLine matchedLine,          // non-null only if status == MATCHED
        List<BankStatementLine> tiedCandidates   // non-empty only if status == AMBIGUOUS
) {
    public static ExactMatchResult matched(BankStatementLine line) {
        return new ExactMatchResult(MatchStatus.MATCHED, line, List.of());
    }

    public static ExactMatchResult noCandidate() {
        return new ExactMatchResult(MatchStatus.NO_CANDIDATE, null, List.of());
    }

    public static ExactMatchResult ambiguous(List<BankStatementLine> tied) {
        return new ExactMatchResult(MatchStatus.AMBIGUOUS, null, tied);
    }
}
