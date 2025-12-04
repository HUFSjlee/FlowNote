package com.flownote.flownote.controller;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.entity.EntryType;
import com.flownote.flownote.repository.EntryRepository;
import com.flownote.flownote.service.TodaySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final TodaySummaryService todaySummaryService;
    private final EntryRepository entryRepository;

    // 홈 화면 (오늘 혹은 선택한 날짜 요약)
    @GetMapping("/")
    public String home(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(value = "date", required = false) LocalDate date,
            Model model
    ) {
        if (date == null) {
            date = LocalDate.now();
        }

        TodaySummaryResponse summary = todaySummaryService.getSummaryByDate(date);
        model.addAttribute("summary", summary);

        return "index"; // templates/index.html
    }

    // 캘린더 화면 + 하단 상세 패널
    @GetMapping("/calendar")
    public String calendar(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(value = "date", required = false) LocalDate date,
            Model model
    ) {
        if (date == null) {
            date = LocalDate.now();
        }

        // 1) 해당 날짜의 모든 엔트리 조회
        List<Entry> entries = entryRepository.findByEntryDate(date);

        // 2) 지출 합계 (type 이 EXPENSE 인 것만 + price null이면 0으로)
        BigDecimal totalExpense = entries.stream()
                .filter(e -> e.getType() == EntryType.EXPENSE)
                .map(e -> e.getPrice() != null ? e.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long expenseCount = entries.stream()
                .filter(e -> e.getType() == EntryType.EXPENSE)
                .count();

        long scheduleCount = entries.stream()
                .filter(e -> e.getType() == EntryType.SCHEDULE)
                .count();

        model.addAttribute("selectedDate", date);
        model.addAttribute("entries", entries);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("expenseCount", expenseCount);
        model.addAttribute("scheduleCount", scheduleCount);

        return "calendar"; // templates/calendar.html
    }
}
