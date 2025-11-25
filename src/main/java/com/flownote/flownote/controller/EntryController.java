package com.flownote.flownote.controller;

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
}


