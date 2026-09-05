package com.razorpay.dataseed;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.entity.TaxLog;
import com.razorpay.repository.BankStatementLineRepository;
import com.razorpay.repository.SettlementRecordRepository;
import com.razorpay.repository.TaxLogRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("seed")
@Order(1)
public class DataSeedRunner implements CommandLineRunner {

    private static final long SEED = 42L;
    private static final String OUTPUT_DIR = "ground-truth";

    private final SyntheticDataGenerator generator;
    private final SettlementRecordRepository settlementRepository;
    private final BankStatementLineRepository bankLineRepository;
    private final TaxLogRepository taxLogRepository;
    private final ObjectMapper objectMapper;

    public DataSeedRunner(
            SyntheticDataGenerator generator,
            SettlementRecordRepository settlementRepository,
            BankStatementLineRepository bankLineRepository,
            TaxLogRepository taxLogRepository,
            ObjectMapper objectMapper) {

        this.generator = generator;
        this.settlementRepository = settlementRepository;
        this.bankLineRepository = bankLineRepository;
        this.taxLogRepository = taxLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        List<GeneratedRecord> batch = generator.generate(SEED);

        System.out.println(
                "Generated " + batch.size()
                        + " synthetic records (seed=" + SEED + ")"
        );
        List<SettlementRecord> settlements = new ArrayList<>();
        List<BankStatementLine> bankLines = new ArrayList<>();
        List<TaxLog> taxLogs = new ArrayList<>();

        for (GeneratedRecord record : batch) {
            settlements.add(record.settlement());
            if (record.bankLine() != null) {
                bankLines.add(record.bankLine());
            }
            taxLogs.add(record.taxLog());
        }
        settlementRepository.saveAll(settlements);
        bankLineRepository.saveAll(bankLines);


        taxLogRepository.saveAll(taxLogs);

        System.out.println(
                "Persisted: "
                        + settlements.size()
                        + " settlements, "
                        + bankLines.size()
                        + " bank lines, "
                        + taxLogs.size()
                        + " tax logs"
        );

        List<GroundTruth> groundTruth = new ArrayList<>();

        for (GeneratedRecord record : batch) {

            Integer bankLineId = null;

            if (record.bankLine() != null) {
                bankLineId = record.bankLine().getLineId();
            }

            groundTruth.add(
                    new GroundTruth(
                            record.settlement().getSettlementId(),
                            record.expectedOutcome().name(),
                            bankLineId,
                            record.note()
                    )
            );
        }
        File outDir = new File(OUTPUT_DIR);

        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        File outFile = new File(
                outDir,
                "ground_truth.json"
        );

        objectMapper
                .writer(SerializationFeature.INDENT_OUTPUT)
                .writeValue(outFile, groundTruth);

        System.out.println(
                "Wrote ground truth for "
                        + groundTruth.size()
                        + " records to "
                        + outFile.getAbsolutePath()
        );
        printBreakdown(batch);
    }

    private void printBreakdown(List<GeneratedRecord> batch) {

        System.out.println("\nIntended breakdown:");

        for (ExpectedOutcome outcome : ExpectedOutcome.values()) {

            long count = batch.stream()
                    .filter(record ->
                            record.expectedOutcome() == outcome
                    )
                    .count();

            if (count > 0) {

                System.out.println(
                        "  "
                                + outcome
                                + ": "
                                + count
                );
            }
        }
    }
}