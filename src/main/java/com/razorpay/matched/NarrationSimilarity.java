package com.razorpay.matched;

public record NarrationSimilarity(double score, String matchedToken, String matchedVariant) {
    static final NarrationSimilarity NONE = new NarrationSimilarity(0.0, null, null);
}