package com.flownote.flownote.service;

import com.flownote.flownote.dto.ConfirmEntryRequest;
import com.flownote.flownote.entity.*;
import com.flownote.flownote.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final EntryRepository entryRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public Entry createDraft(Entry draft) {
        // draft는 EntryParsingService에서 status/type/confidence 등을 채워서 들어옴
        return entryRepository.save(draft);
    }

    @Transactional
    public Entry confirm(Long entryId, ConfirmEntryRequest req) {
        Entry e = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        // 사용자가 확정 화면에서 조정한 값 반영
        e.setType(req.getType());
        e.setEntryDate(req.getEntryDate());
        e.setContent(req.getContent());

        e.setStartDateTime(req.getStartDateTime());
        e.setEndDateTime(req.getEndDateTime());
        e.setLocation(req.getLocation());

        e.setPrice(req.getPrice());
        e.setCategory(req.getCategory());

        if (req.getSyncToGoogle() != null) e.setSyncToGoogle(req.getSyncToGoogle());

        // 확정 처리
        e.setStatus(EntryStatus.CONFIRMED);
        e.setNeedsUserConfirm(false);

        Entry saved = entryRepository.save(e);

        // ✅ 구글 연동은 "확정된 일정 + 연동 ON"일 때만
        boolean shouldSync =
                saved.getType() == EntryType.SCHEDULE
                        && Boolean.TRUE.equals(saved.getSyncToGoogle())
                        && saved.getStartDateTime() != null;

        if (shouldSync && (saved.getGoogleEventId() == null || saved.getGoogleEventId().isBlank())) {
            try {
                saved.setGoogleSyncStatus(GoogleSyncStatus.PENDING);
                String eventId = googleCalendarService.createEventForEntry(saved);
                saved.setGoogleEventId(eventId);
                saved.setGoogleSyncStatus(GoogleSyncStatus.SYNCED);
            } catch (Exception ex) {
                saved.setGoogleSyncStatus(GoogleSyncStatus.FAILED);
            }
            saved = entryRepository.save(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Entry> getConfirmedEntriesInRange(LocalDate from, LocalDate to) {
        return entryRepository.findByStatusAndEntryDateBetween(EntryStatus.CONFIRMED, from, to);
    }

    @Transactional(readOnly = true)
    public List<Entry> getConfirmedEntriesByDay(LocalDate date) {
        return entryRepository.findByStatusAndEntryDate(EntryStatus.CONFIRMED, date);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Entry e = entryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));

        if (e.getStatus() != EntryStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT can be deleted.");
        }

        entryRepository.delete(e);
    }
}
