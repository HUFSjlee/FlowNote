package com.flownote.flownote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "entires")
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

    @Column(columnDefinition = "TEXT")
    private String content;

    private String photoUrl;

    private BigDecimal amount;

    private LocalDateTime createAt = LocalDateTime.now();

}
