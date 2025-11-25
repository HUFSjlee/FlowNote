package com.flownote.flownote.controller;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.service.TodaySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final TodaySummaryService todaySummaryService;

    @GetMapping("/")
    public String home(Model model) {

        TodaySummaryResponse summary = todaySummaryService.getTodaySummary();
        model.addAttribute("summary", summary);
        return "index"; //templates/index.html을 렌더링
    }
}
