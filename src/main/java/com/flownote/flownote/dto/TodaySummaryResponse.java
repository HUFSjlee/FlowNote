package com.flownote.flownote.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TodaySummaryResponse {
    private LocalDate date;
    private BigDecimal totalAmount;
    private int entryCount;
    private List<EntrySummary> entries;

    @Getter
    @Builder
    public static class EntrySummary {
        private Long id;
        private String content;
        private BigDecimal amount;
        private String photoUrl;
        private LocalDateTime createdAt;
    }
}
