package com.flownote.flownote.repository;

import com.flownote.flownote.entity.DailyExpenseSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyExpenseSummaryRepository extends JpaRepository<DailyExpenseSummary, Long> {

    Optional<DailyExpenseSummary> findByDate(LocalDate date);
}
