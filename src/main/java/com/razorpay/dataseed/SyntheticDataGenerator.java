package com.razorpay.dataseed;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.entity.TaxLog;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class SyntheticDataGenerator {

    public static final BigDecimal CONTRACTED_FEE_RATE = new BigDecimal("0.02");
    public static final BigDecimal FEE_RATE_TOLERANCE = new BigDecimal("0.001");
    public static final BigDecimal GST_ON_FEE_RATE = new BigDecimal("0.18");
    public static final BigDecimal TDS_194O_RATE = new BigDecimal("0.01");
    public static final int NORMAL_SETTLEMENT_WINDOW_DAYS = 2;

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 1);

    public List<GeneratedRecord> generate(long seed) {
        Random random = new Random(seed);
        List<GeneratedRecord> records = new ArrayList<>();

        for (int i = 1; i <= 44; i++) {
            records.add(buildClean(random, i));
        }
        records.add(buildGarbledUtr(random, 45));
        records.add(buildGarbledUtr(random, 46));
        records.add(buildNeedsLlmReasoning(random, 47));
        records.add(buildFeeVariance(random, 48));
        records.add(buildTimingDiscrepancy(random, 49));
        records.add(buildMissingReferenceChargeback(random, 50));
        records.addAll(buildDuplicateAmountPair(random, 51, 52));

        return records;
    }

    private GeneratedRecord buildClean(Random random, int index) {
        String settlementId = id("stl", index);
        String utr = randomUtr(random);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomGross(random);

        BigDecimal fee = pct(gross, CONTRACTED_FEE_RATE);
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, utr, settledAt);

        BankStatementLine bankLine = new BankStatementLine(
                cleanNarration(utr), netSettled, settledAt.toLocalDate().plusDays(1), utr);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, bankLine, taxLog, ExpectedOutcome.MATCHED_PASS_1,
                "Clean record: exact UTR + exact amount, within T+1 window.");
    }

    private GeneratedRecord buildGarbledUtr(Random random, int index) {
        String settlementId = id("stl", index);
        String utr = randomUtr(random);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomGross(random);

        BigDecimal fee = pct(gross, CONTRACTED_FEE_RATE);
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, utr, settledAt);

        String messyNarration = "UTIB0000123/RAZOPAY/REF" + utr.substring(utr.length() - 4);
        BankStatementLine bankLine = new BankStatementLine(
                messyNarration, netSettled, settledAt.toLocalDate().plusDays(1), null);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, bankLine, taxLog, ExpectedOutcome.MATCHED_PASS_2,
                "UTR unusable in bank data; narration has a recoverable fragment + merchant name typo.");
    }

    private GeneratedRecord buildNeedsLlmReasoning(Random random, int index) {
        String settlementId = id("stl", index);
        String utr = randomUtr(random);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomGross(random);

        BigDecimal fee = pct(gross, CONTRACTED_FEE_RATE);
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, utr, settledAt);

        String narration = "NEFT-XXXXX-RZRSFTWRE PVT-" + settlement.getPaymentId().substring(4);
        BankStatementLine bankLine = new BankStatementLine(
                narration, netSettled, settledAt.toLocalDate().plusDays(1), null);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, bankLine, taxLog, ExpectedOutcome.MATCHED_PASS_3,
                "No UTR fragment, unrelated-looking narration referencing payment_id instead.");
    }

    private GeneratedRecord buildFeeVariance(Random random, int index) {
        String settlementId = id("stl", index);
        String utr = randomUtr(random);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomGross(random);

        BigDecimal fee = pct(gross, new BigDecimal("0.023"));
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, utr, settledAt);

        BankStatementLine bankLine = new BankStatementLine(
                cleanNarration(utr), netSettled, settledAt.toLocalDate().plusDays(1), utr);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, bankLine, taxLog, ExpectedOutcome.EXCEPTION_FEE_VARIANCE,
                "Fee charged at 2.3% vs. the 2.0% contracted rate. Amount still matches exactly - "
                        + "audit-rule violation, not a matching failure.");
    }

    private GeneratedRecord buildTimingDiscrepancy(Random random, int index) {
        String settlementId = id("stl", index);
        String utr = randomUtr(random);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomGross(random);

        BigDecimal fee = pct(gross, CONTRACTED_FEE_RATE);
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, utr, settledAt);

        BankStatementLine bankLine = new BankStatementLine(
                cleanNarration(utr), netSettled, settledAt.toLocalDate().plusDays(4), utr);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, bankLine, taxLog, ExpectedOutcome.EXCEPTION_TIMING_DISCREPANCY,
                "Bank credit landed T+4 instead of T+1/T+2. UTR + amount are exact, so Pass 1 still "
                        + "matches it - the date-window audit must flag this separately.");
    }

    private GeneratedRecord buildMissingReferenceChargeback(Random random, int index) {
        String settlementId = id("stl_chargeback", index);
        LocalDateTime settledAt = randomSettledAt(random, index);
        BigDecimal gross = randomAmountBetween(random, 500, 5000);

        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal taxOnFee = BigDecimal.ZERO;
        BigDecimal netSettled = gross.negate().setScale(2, RoundingMode.HALF_UP);

        SettlementRecord settlement = new SettlementRecord(settlementId, id("pay_chargeback", index), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, null, settledAt);

        TaxLog taxLog = cleanTaxLog(settlementId, gross, taxOnFee);

        return new GeneratedRecord(settlement, null, taxLog, ExpectedOutcome.EXCEPTION_UNMATCHED_GATEWAY_RECORD,
                "Chargeback deduction with no UTR and no counterpart bank line in this batch at all.");
    }

    private List<GeneratedRecord> buildDuplicateAmountPair(Random random, int index1, int index2) {
        LocalDateTime sharedSettledAt = randomSettledAt(random, index1);
        BigDecimal gross = randomGross(random);
        BigDecimal fee = pct(gross, CONTRACTED_FEE_RATE);
        BigDecimal taxOnFee = pct(fee, GST_ON_FEE_RATE);
        BigDecimal netSettled = netOf(gross, fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO);

        String settlementId1 = id("stl_dup", index1);
        String settlementId2 = id("stl_dup", index2);

        SettlementRecord settlement1 = new SettlementRecord(settlementId1, id("pay_dup", index1), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, null, sharedSettledAt);
        SettlementRecord settlement2 = new SettlementRecord(settlementId2, id("pay_dup", index2), gross,
                fee, taxOnFee, BigDecimal.ZERO, BigDecimal.ZERO, netSettled, null, sharedSettledAt);

        BankStatementLine bankLine1 = new BankStatementLine(
                "RAZORPAY SETTLEMENT", netSettled, sharedSettledAt.toLocalDate().plusDays(1), null);
        BankStatementLine bankLine2 = new BankStatementLine(
                "RAZORPAY SETTLEMENT", netSettled, sharedSettledAt.toLocalDate().plusDays(1), null);

        TaxLog taxLog1 = cleanTaxLog(settlementId1, gross, taxOnFee);
        TaxLog taxLog2 = cleanTaxLog(settlementId2, gross, taxOnFee);

        String note = "Two settlements share identical amount, date, and no UTR; two bank lines share the same "
                + "amount, date, and generic narration. Correct behavior is refusing to force-match either pairing.";

        List<GeneratedRecord> pair = new ArrayList<>();
        pair.add(new GeneratedRecord(settlement1, bankLine1, taxLog1,
                ExpectedOutcome.EXCEPTION_DUPLICATE_AMOUNT_AMBIGUITY, note));
        pair.add(new GeneratedRecord(settlement2, bankLine2, taxLog2,
                ExpectedOutcome.EXCEPTION_DUPLICATE_AMOUNT_AMBIGUITY, note));
        return pair;
    }

    private TaxLog cleanTaxLog(String settlementId, BigDecimal gross, BigDecimal taxOnFee) {
        BigDecimal tds194o = pct(gross, TDS_194O_RATE);
        BigDecimal expectedDeduction = taxOnFee.add(tds194o).setScale(2, RoundingMode.HALF_UP);
        return new TaxLog(settlementId, taxOnFee, tds194o, expectedDeduction);
    }

    private BigDecimal netOf(BigDecimal gross, BigDecimal fee, BigDecimal taxOnFee,
                             BigDecimal reserveHeld, BigDecimal reserveReleased) {
        return gross.subtract(fee).subtract(taxOnFee).subtract(reserveHeld)
                .add(reserveReleased).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private String cleanNarration(String utr) {
        return "RAZORPAY SOFTWARE PVT/" + utr + "/SETTLEMENT";
    }

    private String randomUtr(Random random) {
        StringBuilder sb = new StringBuilder("UTR");
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private BigDecimal randomGross(Random random) {
        return randomAmountBetween(random, 500, 50_000);
    }

    private BigDecimal randomAmountBetween(Random random, int min, int max) {
        int rupees = min + random.nextInt(max - min);
        int paise = random.nextInt(100);
        return new BigDecimal(rupees + "." + String.format("%02d", paise));
    }

    private LocalDateTime randomSettledAt(Random random, int index) {
        int dayOffset = random.nextInt(30);
        int hour = random.nextInt(24);
        int minute = random.nextInt(60);
        return BASE_DATE.plusDays(dayOffset).atTime(hour, minute);
    }

    private String id(String prefix, int index) {
        return prefix + "_" + String.format("%04d", index);
    }
}