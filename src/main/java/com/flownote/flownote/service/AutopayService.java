package com.flownote.flownote.service;

import com.flownote.flownote.entity.Autopay;
import com.flownote.flownote.repository.AutopayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AutopayService {

    private final AutopayRepository autopayRepository;
    private final GoogleCalendarService googleCalendarService;

    /**
     * 자동이체 등록 + 구글 캘린더에 매월 반복 all-day 이벤트 생성
     */
    public Autopay registerAutopay(String title, BigDecimal amount, Integer dayOfMonth, String memo) {

        // 첫 시작일: 이번 달 dayOfMonth, 이미 지났으면 다음 달
        LocalDate now = LocalDate.now();
        LocalDate firstDate = now.withDayOfMonth(Math.min(dayOfMonth, now.lengthOfMonth()));
        if (firstDate.isBefore(now)) {
            LocalDate nextMonth = now.plusMonths(1);
            firstDate = nextMonth.withDayOfMonth(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        }

        String summary = String.format("[자동이체] %s - ₩%,d", title, amount.intValue());
        String description = (memo != null ? memo : "") + "\nFlowNote에서 등록된 자동이체입니다.";

        String eventId = googleCalendarService.createMonthlyRecurringAllDayEvent(
                firstDate,
                dayOfMonth,
                summary,
                description
        );

        Autopay autopay = Autopay.builder()
                .title(title)
                .amount(amount)
                .dayOfMonth(dayOfMonth)
                .memo(memo)
                .googleEventId(eventId)
                .build();

        return autopayRepository.save(autopay);
    }
}
