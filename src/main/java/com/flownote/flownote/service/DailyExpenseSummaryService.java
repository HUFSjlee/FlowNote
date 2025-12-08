package com.flownote.flownote.service;

import com.flownote.flownote.entity.DailyExpenseSummary;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.entity.EntryType;
import com.flownote.flownote.repository.DailyExpenseSummaryRepository;
import com.flownote.flownote.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyExpenseSummaryService {

    private final EntryRepository entryRepository;
    private final DailyExpenseSummaryRepository dailyExpenseSummaryRepository;
    private final GoogleCalendarService googleCalendarService;

    /**
     * 특정 날짜의 지출 합계를 다시 계산하고,
     * DB + 구글 캘린더 all-day 이벤트를 업데이트/생성한다.
     */
    public void updateDailyExpenseSummary(LocalDate date) {

        List<Entry> entries = entryRepository.findByEntryDate(date);

        BigDecimal totalExpense = entries.stream()
                .filter(e -> e.getType() == EntryType.EXPENSE)
                .map(e -> e.getPrice() != null ? e.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 아직 지출이 0이면: 일단 이벤트 생성/유지는 나중에 정책 정하고, 지금은 그냥 패스해도 됨
        if (totalExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String summaryText = String.format("지출 합계 ₩%,d", totalExpense.intValue());
        String description = "FlowNote에서 자동 생성된 하루 지출 합계입니다.";

        DailyExpenseSummary summary = dailyExpenseSummaryRepository.findByDate(date)
                .orElseGet(() -> DailyExpenseSummary.builder()
                        .date(date)
                        .totalAmount(BigDecimal.ZERO)
                        .build()
                );

        if (summary.getGoogleEventId() == null) {
            // 새 이벤트 생성
            String eventId = googleCalendarService.createAllDayEvent(date, summaryText, description);
            summary.setGoogleEventId(eventId);
        } else {
            // 기존 이벤트 업데이트
            googleCalendarService.updateAllDayEvent(summary.getGoogleEventId(), date, summaryText, description);
        }

        summary.setTotalAmount(totalExpense);
        dailyExpenseSummaryRepository.save(summary);
    }
}
