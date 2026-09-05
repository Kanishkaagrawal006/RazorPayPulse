package com.razorpay.repository;

import com.razorpay.entity.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kanu
 **/
public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Integer> {
}
