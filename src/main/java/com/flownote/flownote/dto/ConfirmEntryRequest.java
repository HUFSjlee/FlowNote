package com.flownote.flownote.dto;

import com.flownote.flownote.entity.EntryType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class ConfirmEntryRequest {
    private EntryType type;
    private LocalDate entryDate;
    private String content;

    //일정
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String location;

    //지출
    private BigDecimal price;
    private String category;

    //구글 연동 옵션
    private Boolean syncToGoogle;
}
