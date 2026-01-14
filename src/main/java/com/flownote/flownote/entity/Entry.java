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

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Enumerated(EnumType.STRING)
    private EntryStatus status;   // ✅ DRAFT / CONFIRMED

    @Enumerated(EnumType.STRING)
    private EntrySource source;   // ✅ MANUAL / AI

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String photoUrl;

    // 가계부용
    private BigDecimal price;
    private String category;

    // 일정용
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String location;

    // ✅ AI 품질/사용성 어필용
    private Double confidence;          // 0.0 ~ 1.0
    private Boolean needsUserConfirm;   // 애매하면 true

    // ✅ 구글 캘린더 연동 옵션/상태
    private Boolean syncToGoogle; // 사용자가 연동 ON일 때만 true
    @Enumerated(EnumType.STRING)
    private GoogleSyncStatus googleSyncStatus;

    private String googleEventId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // 기본값(안전)
        if (status == null) status = EntryStatus.DRAFT;
        if (source == null) source = EntrySource.MANUAL;
        if (needsUserConfirm == null) needsUserConfirm = true;
        if (syncToGoogle == null) syncToGoogle = false;
        if (googleSyncStatus == null) googleSyncStatus = GoogleSyncStatus.NOT_REQUESTED;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
