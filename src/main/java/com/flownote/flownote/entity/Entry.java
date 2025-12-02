package com.flownote.flownote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "entries")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Entry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    //사용자가 입력한 원본 텍스트
    @Column(columnDefinition = "TEXT")
    private String rawContent;

    //AI가 정제/요약한 텍스트
    @Column(columnDefinition = "TEXT")
    private String content;

    private String photoUrl;

    //가계부용
    private BigDecimal price;
    private String category;

    //일정용
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String location;

    //구글 캘린더 연동용
    private String googleEventId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
