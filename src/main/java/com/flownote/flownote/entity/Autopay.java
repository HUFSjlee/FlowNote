package com.flownote.flownote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "autopay")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Autopay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예: "넷플릭스", "OO카드 할부", "월세"
    private String title;

    // 자동이체 금액
    private BigDecimal amount;

    // 매월 몇 일에 나가는지 (1~31)
    private Integer dayOfMonth;

    // 메모/설명
    @Column(columnDefinition = "TEXT")
    private String memo;

    // 구글 캘린더 이벤트 ID (반복 이벤트)
    private String googleEventId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
