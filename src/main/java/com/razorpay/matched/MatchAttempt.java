package com.razorpay.matched;

import com.razorpay.entity.SettlementRecord;

public record MatchAttempt(SettlementRecord settlement, ExactMatchResult result) {
}
