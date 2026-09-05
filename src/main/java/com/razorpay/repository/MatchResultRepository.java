package com.razorpay.repository;

import com.razorpay.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * @author kanu
 **/
public interface MatchResultRepository extends JpaRepository<MatchResult, Integer> {
    @Query("select m.bankLineId from MatchResult m where m.bankLineId is not null")
    List<Integer> findAllMatchedBankLineIds();
}
