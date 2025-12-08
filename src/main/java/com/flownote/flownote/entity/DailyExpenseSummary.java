package com.flownote.flownote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_expense_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyExpenseSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    private BigDecimal totalAmount;

    // 이 날짜의 합계를 나타내는 구글 이벤트 ID
    private String googleEventId;

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    @PrePersist
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
