package com.flownote.flownote.service;

import com.flownote.flownote.entity.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntryParsingService {

    private static final Pattern WON_PATTERN = Pattern.compile("(\\d{1,9})\\s*원");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,9})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})\\s*시(\\s*(\\d{1,2})\\s*분)?");


    public Entry parseToDraft(String text, Boolean syncToGoogle) {
        String t = text == null ? "" : text.trim();

        Entry e = new Entry();
        e.setRawContent(t);
        e.setContent(t);
        e.setEntryDate(LocalDate.now());

        e.setStatus(EntryStatus.DRAFT);
        e.setSource(EntrySource.AI);
        e.setSyncToGoogle(Boolean.TRUE.equals(syncToGoogle));
        e.setGoogleSyncStatus(GoogleSyncStatus.NOT_REQUESTED);

        // 기본값
        e.setNeedsUserConfirm(true);
        e.setConfidence(0.60);

        // 1) 1차 룰 분류
        boolean moneyLike = hasMoneyLike(t);
        boolean scheduleLike = looksScheduleLike(t);

        // 우선순위: (일정 신호만 강하면 일정) / (돈 신호 있으면 지출)
        if (scheduleLike && !moneyLike) {
            e.setType(EntryType.SCHEDULE);
            e.setConfidence(0.75);

            // ✅ 날짜: 오늘/내일/모레 처리 (이미 만든 resolveDate 사용)
            LocalDate date = resolveDate(t);
            e.setEntryDate(date);

            // ✅ 시간: "20시", "18시 30분" 등 추출해서 start/end 채우기
            Matcher tm = TIME_PATTERN.matcher(t);
            if (tm.find()) {
                int hour = Integer.parseInt(tm.group(1));
                int minute = (tm.group(3) != null) ? Integer.parseInt(tm.group(3)) : 0;

                LocalDateTime start = date.atTime(hour, minute);
                e.setStartDateTime(start);
                e.setEndDateTime(start.plusHours(1)); // 기본 1시간
            } else {
                // 시간이 없으면 null 유지 (사용자가 입력하도록)
                e.setStartDateTime(null);
                e.setEndDateTime(null);
            }

            // ✅ 장소: "시" 뒤 단어 or "~에서" 형태로 추출 (이미 만든 guessLocation 사용)
            e.setLocation(guessLocation(t));

            // 일정은 보통 사용자 확인 필요(시간/장소/종료 등)
            e.setNeedsUserConfirm(true);
        } else if (moneyLike) {
            e.setType(EntryType.EXPENSE);
            e.setPrice(extractAmountFlexible(t));
            e.setCategory(guessCategory(t));
            e.setConfidence(0.80);
        } else {
            e.setType(EntryType.NOTE);
            e.setNeedsUserConfirm(false); // 메모는 확인 요구 덜 해도 됨
            e.setConfidence(0.80);
        }

        // 2) guardrail(검증/보정)
        applyGuardrail(e);

        return e;
    }

    private void applyGuardrail(Entry e) {
        // NOTE인데 금액이 있으면 EXPENSE로 승격
        if (e.getType() == EntryType.NOTE && e.getPrice() != null && e.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            e.setType(EntryType.EXPENSE);
            e.setNeedsUserConfirm(true);
            e.setConfidence(Math.min(0.70, e.getConfidence())); // 확신도는 낮게
        }

        // EXPENSE인데 금액이 0이면 확인 필요
        if (e.getType() == EntryType.EXPENSE) {
            if (e.getPrice() == null || e.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                e.setNeedsUserConfirm(true);
                e.setConfidence(Math.min(0.60, e.getConfidence()));
            }
        }

        // SCHEDULE인데 시간/날짜 단서가 약하면 확인 필요
        if (e.getType() == EntryType.SCHEDULE) {
            // 지금은 startDateTime 추출을 안 하니, 최소한 "시간 단서"가 없으면 확인 필요로 유지
            // (AI 붙이면 여기서 start/end 채워지고 confidence가 올라감)
            e.setNeedsUserConfirm(true);
        }
    }

    private boolean hasMoneyLike(String t) {
        if (WON_PATTERN.matcher(t).find()) return true;

        // 숫자만 있는 경우는 "지출 관련 단서"와 같이 있을 때만 돈으로 인정
        boolean hasNumber = NUMBER_PATTERN.matcher(t).find();
        boolean hasExpenseHint = containsAny(t,
                "투썸", "스타벅스", "커피", "카페",
                "편의점", "GS", "CU", "세븐",
                "결제", "카드", "지출",
                "점심", "저녁", "밥", "식사", "먹"
        );
        return hasNumber && hasExpenseHint;
    }

    private boolean looksScheduleLike(String t) {
        return containsAny(t,
                "약속", "회의", "미팅", "면접", "병원", "수업", "방문",
                "내일", "모레", "다음주", "다음 주", "이번주", "이번 주"
        ) || TIME_PATTERN.matcher(t).find();
    }

    private BigDecimal extractAmountFlexible(String text) {
        Matcher m = WON_PATTERN.matcher(text);
        if (m.find()) return new BigDecimal(m.group(1));

        Matcher m2 = NUMBER_PATTERN.matcher(text);
        if (m2.find()) return new BigDecimal(m2.group(1));

        return BigDecimal.ZERO;
    }

    private String guessCategory(String t) {
        if (containsAny(t, "투썸", "스타벅스", "커피", "카페")) return "카페";
        if (containsAny(t, "편의점", "GS", "CU", "세븐")) return "편의점";
        if (containsAny(t, "점심", "저녁", "밥", "식사", "먹")) return "식비";
        return null;
    }

    private boolean containsAny(String t, String... keywords) {
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    private LocalDate resolveDate(String t) {
        LocalDate today = LocalDate.now();
        if (t.contains("내일")) return today.plusDays(1);
        if (t.contains("모레")) return today.plusDays(2);
        if (t.contains("오늘")) return today;

        // 다음주/이번주는 일단 보수적으로: 확인 필요로 두고 today 반환
        // (AI 붙일 때 정확히)
        return today;
    }

    private LocalDateTime resolveStartDateTime(String t, LocalDate date) {
        Matcher m = TIME_PATTERN.matcher(t);
        if (!m.find()) return null;

        int hour = Integer.parseInt(m.group(1));
        int minute = 0;
        if (m.group(3) != null) minute = Integer.parseInt(m.group(3));

        // "오후 8시" 같은 표현은 아직 미지원(추가 가능)
        // 여기서는 24시간 표기로 들어온다고 가정
        return date.atTime(hour, minute);
    }

    private String guessLocation(String t) {
        // 예: "내일 20시 강남 약속" -> "강남"
        // 1) "시" 뒤의 첫 단어를 장소 후보로 잡는다
        Pattern p = Pattern.compile("시\\s*([가-힣A-Za-z0-9]+)");
        Matcher m = p.matcher(t);
        if (m.find()) {
            String candidate = m.group(1);
            // 약속/회의 같은 단어는 장소가 아니므로 걸러냄
            if (!containsAny(candidate, "약속", "회의", "미팅", "수업", "병원")) {
                return candidate;
            }
        }

        // 2) "에서" 앞 단어도 장소 후보
        Pattern p2 = Pattern.compile("([가-힣A-Za-z0-9]+)에서");
        Matcher m2 = p2.matcher(t);
        if (m2.find()) return m2.group(1);

        return null;
    }

    private BigDecimal extractAmount(String text) {
        Matcher m = WON_PATTERN.matcher(text);
        if (m.find()) return new BigDecimal(m.group(1));

        Matcher m2 = NUMBER_PATTERN.matcher(text);
        if (m2.find()) return new BigDecimal(m2.group(1));

        return BigDecimal.ZERO;
    }
}
