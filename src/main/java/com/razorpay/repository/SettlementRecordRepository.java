package com.razorpay.repository;

import com.razorpay.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRecordRepository
        extends JpaRepository<SettlementRecord, String> {
    List<SettlementRecord> findByReconciliationStatus(SettlementRecord.ReconciliationStatus status);

}