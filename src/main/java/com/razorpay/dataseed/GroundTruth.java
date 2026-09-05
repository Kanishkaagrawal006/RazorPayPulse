package com.razorpay.dataseed;

public record GroundTruth(
        String settlementId,
        String expectedOutcome,
        Integer bankLineId,
        String note
) {
}