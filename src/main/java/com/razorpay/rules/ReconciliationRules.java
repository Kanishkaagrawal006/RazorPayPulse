package com.razorpay.rules;

import java.math.BigDecimal;
import java.util.List;

public final class ReconciliationRules {
    public static final BigDecimal CONTRACTED_FEE_RATE = new BigDecimal("0.02");
    public static final BigDecimal FEE_RATE_TOLERANCE = new BigDecimal("0.001");
    public static final BigDecimal GST_ON_FEE_RATE = new BigDecimal("0.18");
    public static final BigDecimal TDS_194O_RATE = new BigDecimal("0.01");
    public static final int NORMAL_SETTLEMENT_WINDOW_DAYS = 2;
    public static final double FUZZY_MATCH_THRESHOLD = 0.85;
    public static final int FUZZY_DATE_TOLERANCE_DAYS = 5;
    public static final List<String> MERCHANT_NAME_VARIANTS =
            List.of("RAZORPAY", "RAZORPAY SOFTWARE", "SOFTWARE");
    public static final int LLM_DATE_TOLERANCE_DAYS = 7;
    public static final double LLM_MATCH_CONFIDENCE_THRESHOLD = 0.85;


    private ReconciliationRules() {
    }
}