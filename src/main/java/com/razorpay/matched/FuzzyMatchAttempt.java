package com.razorpay.matched;

import com.razorpay.entity.SettlementRecord;

public record FuzzyMatchAttempt(SettlementRecord settlement, FuzzyMatchResult result) {
}