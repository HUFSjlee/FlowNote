package com.flownote.flownote.repository;

import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.entity.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {
    List<Entry> findByEntryDate(LocalDate entryDate);

    //달력 월 범위 조회용
    List<Entry> findByEntryDateBetween(LocalDate from, LocalDate to);

    //확정된 것만 달력에 표시
    List<Entry> findByStatusAndEntryDateBetween(EntryStatus status, LocalDate from, LocalDate to);

    List<Entry> findByStatusAndEntryDate(EntryStatus status, LocalDate date);
}
