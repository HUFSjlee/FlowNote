package com.flownote.flownote.dto;

import com.flownote.flownote.entity.EntryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AiParsedEntryResult(
        EntryType type,
        LocalDate entryDate,
        String content,
        BigDecimal price,
        String category,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String location,
        Double confidence
) {}
