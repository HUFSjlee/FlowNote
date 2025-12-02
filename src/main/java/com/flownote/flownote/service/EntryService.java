package com.flownote.flownote.service;

import com.flownote.flownote.entity.Entry;
import com.flownote.flownote.entity.EntryType;
import com.flownote.flownote.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final EntryRepository entryRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public Entry saveEntry(Entry entry) {

        // 1. 우선 DB에 저장
        Entry saved = entryRepository.save(entry);

        // 2. 일정 타입이면 구글 캘린더에 이벤트 생성
        if (saved.getType() == EntryType.SCHEDULE && saved.getStartDateTime() != null) {
            try {
                String eventId = googleCalendarService.createEventForEntry(saved);
                saved.setGoogleEventId(eventId);
            } catch (Exception e) {
                // 일단 로그만 찍고, 일정 생성 실패해도 Entry 저장은 계속 가는 방향
                // 나중에 실패 내역을 따로 보는 화면 만들어도 좋음
                e.printStackTrace();
            }
        }

        return saved;
    }
}
