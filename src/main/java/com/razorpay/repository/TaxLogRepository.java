package com.razorpay.repository;

import com.razorpay.entity.TaxLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kanu
 **/
public interface TaxLogRepository extends JpaRepository<TaxLog, String> {
}