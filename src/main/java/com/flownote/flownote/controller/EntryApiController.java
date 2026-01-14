package com.flownote.flownote.controller;

import com.flownote.flownote.dto.ConfirmEntryRequest;
import com.flownote.flownote.dto.ParseEntryRequest;
import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.service.EntryParsingService;
import com.flownote.flownote.service.EntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/entries")
public class EntryApiController {
    private final EntryParsingService entryParsingService;
    private final EntryService entryService;

    // 1) 한 줄 입력 -> DRAFT 생성
    @PostMapping("/parse")
    public Entry parse(@RequestBody ParseEntryRequest req) {
        Entry draft = entryParsingService.parseToDraft(req.getText(), req.getSyncToGoogle());
        return entryService.createDraft(draft);
    }

    // 2) DRAFT -> CONFIRMED 확정
    @PostMapping("/{id}/confirm")
    public Entry confirm(@PathVariable Long id, @RequestBody ConfirmEntryRequest req) {
        return entryService.confirm(id, req);
    }

    // 3) 달력 월 범위 조회 (CONFIRMED만)
    @GetMapping
    public List<Entry> getRange(@RequestParam String from, @RequestParam String to) {
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        return entryService.getConfirmedEntriesInRange(f, t);
    }

    // 4) 특정 날짜 상세 조회 (CONFIRMED만)
    @GetMapping("/day")
    public List<Entry> getDay(@RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        return entryService.getConfirmedEntriesByDay(d);
    }

    @DeleteMapping("/{id}")
    public void deleteDraft(@PathVariable Long id) {
        entryService.deleteDraft(id);
    }
}
