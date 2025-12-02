package com.flownote.flownote.controller;

import com.flownote.flownote.dto.TodaySummaryResponse;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.entity.EntryType;
import com.flownote.flownote.repository.EntryRepository;
import com.flownote.flownote.service.EntryService;
import com.flownote.flownote.service.S3Service;
import com.flownote.flownote.service.TodaySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class EntryController {

    private final EntryRepository entryRepository;
    private final EntryService entryService;
    private final S3Service s3Service;
    private final TodaySummaryService todaySummaryService;

    // 오늘 기록 조회
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
        entry.setRawContent(content);
        entry.setContent(content);
        entry.setPrice(amount != null ? amount : BigDecimal.ZERO);
        entry.setType(EntryType.EXPENSE); // 일단 기본은 지출로 가정

        return entryService.saveEntry(entry);
    }

    // 사진 업로드 (로컬)
    @PostMapping("/upload")
    public String uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get("uploads/" + fileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());
        return filePath.toString();
    }

    // 텍스트 + 금액 + 사진 (S3)
    @PostMapping("/entry/photo")
    public Entry addEntryWithPhoto(@RequestParam String content,
                                   @RequestParam(required = false) BigDecimal amount,
                                   @RequestParam("file") MultipartFile file) throws IOException {

        String photoUrl = s3Service.uploadFile(file);

        Entry entry = new Entry();
        entry.setEntryDate(LocalDate.now());
        entry.setRawContent(content);
        entry.setContent(content);
        entry.setPrice(amount != null ? amount : BigDecimal.ZERO);
        entry.setPhotoUrl(photoUrl);
        entry.setType(EntryType.EXPENSE);

        return entryService.saveEntry(entry);
    }

    // 오늘 요약
    @GetMapping("/today/summary")
    public TodaySummaryResponse getTodaySummary() {
        return todaySummaryService.getTodaySummary();
    }

    // ✅ 테스트용 일정 생성 엔드포인트
    @GetMapping("/test/schedule")
    public Entry createTestSchedule() {

        LocalDateTime now = LocalDateTime.now();

        Entry entry = new Entry();
        entry.setEntryDate(now.toLocalDate());
        entry.setType(EntryType.SCHEDULE);
        entry.setRawContent("FlowNote 테스트 일정");
        entry.setContent("FlowNote 테스트 일정");
        entry.setStartDateTime(now.plusMinutes(10));   // 10분 뒤 시작
        entry.setEndDateTime(now.plusHours(1));        // 1시간짜리 일정
        entry.setLocation("Online");

        return entryService.saveEntry(entry);
    }
}
