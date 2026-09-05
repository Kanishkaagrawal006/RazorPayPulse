package com.razorpay.repository;

import com.razorpay.entity.ExceptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kanu
 **/
public interface ExceptionRecordRepository extends JpaRepository<ExceptionRecord, Integer> {
}