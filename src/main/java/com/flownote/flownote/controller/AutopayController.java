package com.flownote.flownote.controller;

import com.flownote.flownote.entity.Autopay;
import com.flownote.flownote.service.AutopayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/autopay")
public class AutopayController {

    private final AutopayService autopayService;

    /**
     * 예시 호출:
     * POST /api/autopay
     * title=넷플릭스&amount=14500&dayOfMonth=15&memo=프리미엄 요금제
     */
    @PostMapping
    public Autopay registerAutopay(@RequestParam String title,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam Integer dayOfMonth,
                                   @RequestParam(required = false) String memo) {
        return autopayService.registerAutopay(title, amount, dayOfMonth, memo);
    }
}
