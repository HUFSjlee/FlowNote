package com.flownote.flownote.service;

import com.flownote.flownote.entity.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntryParsingService {

    private static final Pattern WON_PATTERN = Pattern.compile("(\\d{1,9})\\s*원");
    private static final Pattern NUM_PATTERN = Pattern.compile("(\\d{1,9})");

    public Entry parseToDraft(String text, Boolean syncToGoogle) {
        String t = text == null ? "" : text.trim();

        Entry e = new Entry();
        e.setRawContent(t);
        e.setContent(t);

        // 기준 날짜: 일단 오늘 (나중에 AI가 날짜를 뽑으면 덮어쓰기)
        e.setEntryDate(LocalDate.now());

        // ✅ 너가 추가한 필드들 기준(없으면 컴파일 에러 날 수 있으니 엔티티에 맞춰 조정)
        e.setStatus(EntryStatus.DRAFT);
        e.setSource(EntrySource.AI);
        e.setNeedsUserConfirm(true);
        e.setConfidence(0.65);

        // 연동 여부(초안 단계에서는 “표시만” 저장, 실제 구글 반영은 confirm에서만)
        e.setSyncToGoogle(Boolean.TRUE.equals(syncToGoogle));
        e.setGoogleSyncStatus(GoogleSyncStatus.NOT_REQUESTED);

        // --- 아주 단순한 분류 룰 (MVP용) ---
        boolean looksExpense = t.contains("원") || t.contains("결제") || t.contains("카드");
        boolean looksSchedule = t.contains("약속") || t.contains("회의") || t.contains("미팅")
                || t.contains("내일") || t.contains("다음") || t.contains("시");

        if (looksExpense && !looksSchedule) {
            e.setType(EntryType.EXPENSE);
            e.setPrice(extractAmount(t));
            e.setConfidence(0.75);
        } else if (looksSchedule) {
            e.setType(EntryType.SCHEDULE);
            e.setConfidence(0.70);
        } else {
            e.setType(EntryType.NOTE);
            e.setNeedsUserConfirm(false);
            e.setConfidence(0.80);
        }

        return e;
    }

    private BigDecimal extractAmount(String text) {
        Matcher m = WON_PATTERN.matcher(text);
        if (m.find()) return new BigDecimal(m.group(1));

        Matcher m2 = NUM_PATTERN.matcher(text);
        if (m2.find()) return new BigDecimal(m2.group(1));

        return BigDecimal.ZERO;
    }
}
