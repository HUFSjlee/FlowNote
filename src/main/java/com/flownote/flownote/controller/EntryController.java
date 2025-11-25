package com.flownote.flownote.controller;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.repository.EntryRepository;
import com.flownote.flownote.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class EntryController {

    private final EntryRepository entryRepository;
    private final S3Service s3Service;

    //오늘 기록 조회
    @GetMapping("/today")
    public List<Entry> getTodayEntries() {
        return entryRepository.findByEntryDate(LocalDate.now());
    }

    // 새 기록 추가 (텍스트 + 금액)
    @PostMapping("/entry")
    public Entry addEntry(@RequestParam String content,
                          @RequestParam(required = false) BigDecimal amount) {
        Entry entry = new Entry();
        entry.setEntryDate(LocalDate.now());
        entry.setContent(content);
        entry.setAmount(amount != null ? amount : BigDecimal.ZERO);
        return entryRepository.save(entry);
    }

    // 사진 업로드
    @PostMapping("/upload")
    public String uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get("uploads/" + fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());
        return filePath.toString();  // 나중에 DB에 photoUrl로 저장 가능
    }

    @PostMapping("/entry/photo")
    public Entry addEntryWithPhoto(@RequestParam String content,
                                   @RequestParam(required = false) BigDecimal amount,
                                   @RequestParam("file") MultipartFile file) throws IOException {
        String photoUrl = s3Service.uploadFile(file);

        Entry entry = new Entry();
        entry.setEntryDate(LocalDate.now());
        entry.setContent(content);
        entry.setAmount(amount != null ? amount : BigDecimal.ZERO);
        entry.setPhotoUrl(photoUrl);

        return entryRepository.save(entry);
    }

    @GetMapping("/today/summary")
    public TodaySummaryResponse getTodaySummary() {
        LocalDate today = LocalDate.now();

        // 오늘 기록한 것 전부 조회
        List<Entry> entries = entryRepository.findByEntryDate(today);

        // 오늘 사용 금액 합계 (단, amount 가 null 이면 0으로 취급)
        BigDecimal totalAmount = entries.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 화면에 넘겨줄 summary 리스트로 변환
        List<TodaySummaryResponse.EntrySummary> entrySummaries = entries.stream()
                .map(e -> TodaySummaryResponse.EntrySummary.builder()
                .id(e.getId())
                .content(e.getContent()).amount(e.getAmount())
                        .photoUrl(e.getPhotoUrl())
                        .createdAt(e.getCreateAt())
                        .build()
                )
                .toList();

        return TodaySummaryResponse.builder()
                .date(today)
                .totalAmount(totalAmount)
                .entryCount(entries.size())
                .entries(entrySummaries)
                .build();
    }
}


