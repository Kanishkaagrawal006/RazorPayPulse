package com.razorpay.matched;

import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.ExceptionRecord;
import com.razorpay.entity.MatchResult;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.repository.ExceptionRecordRepository;
import com.razorpay.repository.MatchResultRepository;
import com.razorpay.repository.SettlementRecordRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class MatchPersistenceService {

    private static final int MAX_RETRY_ON_LOCK_CONFLICT = 3;

    private final SettlementRecordRepository settlementRepository;
    private final MatchResultRepository matchResultRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;

    public MatchPersistenceService(SettlementRecordRepository settlementRepository,
                                   MatchResultRepository matchResultRepository,
                                   ExceptionRecordRepository exceptionRecordRepository) {
        this.settlementRepository = settlementRepository;
        this.matchResultRepository = matchResultRepository;
        this.exceptionRecordRepository = exceptionRecordRepository;
    }

    @Transactional
    public void persistMatch(String settlementId, Integer bankLineId, short pass,
                             BigDecimal confidence, String[] rulesApplied, String reasoning) {
        int attempt = 0;
        while (true) {
            try {
                SettlementRecord fresh = settlementRepository.findById(settlementId)
                        .orElseThrow(() -> new IllegalStateException("Settlement disappeared: " + settlementId));
                fresh.markMatched();
                settlementRepository.save(fresh);

                matchResultRepository.save(new MatchResult(
                        settlementId, bankLineId, pass, confidence, rulesApplied, reasoning));
                return;
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ON_LOCK_CONFLICT) {
                    throw e;
                }
            }
        }
    }

    @Transactional
    public void recordAuditException(ExceptionRecord exceptionRecord) {
        exceptionRecordRepository.save(exceptionRecord);
    }

    @Transactional
    public void persistAmbiguous(String settlementId, int tiedCandidateCount) {
        int attempt = 0;
        while (true) {
            try {
                SettlementRecord fresh = settlementRepository.findById(settlementId)
                        .orElseThrow(() -> new IllegalStateException("Settlement disappeared: " + settlementId));
                fresh.markAmbiguous();
                settlementRepository.save(fresh);

                exceptionRecordRepository.save(new ExceptionRecord(settlementId,
                        ExceptionRecord.Category.Duplicate_Amount_Ambiguity,
                        "Pass 1 found " + tiedCandidateCount
                                + " bank lines with identical UTR + amount - refusing to force-pick one."));
                return;
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ON_LOCK_CONFLICT) {
                    throw e;
                }
            }
        }
    }
    @Transactional
    public void persistUnresolved(String settlementId, ExceptionRecord exceptionRecord) {
        int attempt = 0;
        while (true) {
            try {
                SettlementRecord fresh = settlementRepository.findById(settlementId)
                        .orElseThrow(() -> new IllegalStateException("Settlement disappeared: " + settlementId));
                fresh.markUnresolved();
                settlementRepository.save(fresh);
                exceptionRecordRepository.save(exceptionRecord);
                return;
            } catch (OptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= MAX_RETRY_ON_LOCK_CONFLICT) {
                    throw e;
                }
            }
        }
    }
}