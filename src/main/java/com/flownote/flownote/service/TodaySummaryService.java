package com.flownote.flownote.service;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TodaySummaryService {

    private final EntryRepository entryRepository;

    /**
     * 특정 날짜(date)의 기록 요약 조회
     */
    public TodaySummaryResponse getSummaryByDate(LocalDate date) {
        // 1) 해당 날짜 기록 조회
        List<Entry> entries = entryRepository.findByEntryDate(date);

        // 2) 금액 합계 (null이면 0으로 처리)
        BigDecimal totalAmount = entries.stream()
                .map(e -> e.getPrice() != null ? e.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3) DTO 변환
        List<TodaySummaryResponse.EntrySummary> entrySummaries = entries.stream()
                .map(e -> TodaySummaryResponse.EntrySummary.builder()
                        .id(e.getId())
                        .content(e.getContent())
                        .amount(e.getPrice())
                        .photoUrl(e.getPhotoUrl())
                        .createdAt(e.getCreatedAt())
                        .build()
                )
                .toList();

        // 4) 최종 응답 DTO
        return TodaySummaryResponse.builder()
                .date(date)
                .totalAmount(totalAmount)
                .entryCount(entries.size())
                .entries(entrySummaries)
                .build();
    }

    /**
     * 오늘 날짜 기준 기록 요약 조회
     */
    public TodaySummaryResponse getTodaySummary() {
        return getSummaryByDate(LocalDate.now());
    }
}
