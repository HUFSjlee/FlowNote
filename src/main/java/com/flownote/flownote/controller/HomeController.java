package com.flownote.flownote.controller;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.service.TodaySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final TodaySummaryService todaySummaryService;

    @GetMapping("/")
    public String home(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(value = "date", required = false) LocalDate date,
            Model model
    ) {
        // date 파라미터가 없으면 오늘로
        if (date == null) {
            date = LocalDate.now();
        }

        TodaySummaryResponse summary = todaySummaryService.getSummaryByDate(date);
        model.addAttribute("summary", summary);

        return "index"; // templates/index.html
    }

    @GetMapping("/calendar")
    public String calendar() {
        // 지금은 단순히 템플릿만 렌더링
        return "calendar"; // templates/calendar.html
    }
}
