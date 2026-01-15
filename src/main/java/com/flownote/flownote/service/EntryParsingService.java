package com.flownote.flownote.service;

import com.flownote.flownote.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.flownote.flownote.dto.AiParsedEntryResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class EntryParsingService {

    private static final Pattern WON_PATTERN = Pattern.compile("(\\d{1,9})\\s*원");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,9})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})\\s*시(\\s*(\\d{1,2})\\s*분)?");

    private final OpenAiParsingService openAiParsingService;

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

        AiParsedEntryResult ai = openAiParsingService.parse(t);

        if (ai != null && ai.type() != null) {
            e.setType(ai.type());

            // AI가 날짜를 주면 그걸 사용, 없으면 현재 기본값(LocalDate.now()) 유지
            if (ai.entryDate() != null) e.setEntryDate(ai.entryDate());

            // content는 "정제된 내용"으로 덮어쓰기(없으면 원문 유지)
            if (ai.content() != null && !ai.content().isBlank()) e.setContent(ai.content());

            // 지출/일정 필드들 세팅 (null이면 그대로 둠)
            e.setPrice(ai.price());
            e.setCategory(ai.category());
            e.setStartDateTime(ai.startDateTime());
            e.setEndDateTime(ai.endDateTime());
            e.setLocation(ai.location());

            // confidence/confirm 정책
            double conf = (ai.confidence() != null) ? ai.confidence() : 0.65;
            e.setConfidence(conf);

            // confidence가 충분히 높으면 confirm 덜 요구 (임계값은 나중에 조정)
            e.setNeedsUserConfirm(conf < 0.80);

            // 가드레일 역할
            normalizeAiResult(e, t);

            // ✅ guardrail로 마지막 보정
            applyGuardrail(e);

            return e;
        }

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

            // 금액이 없거나 0이면 무조건 확인 필요
            if (e.getPrice() == null || e.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                e.setNeedsUserConfirm(true);
                e.setConfidence(Math.min(0.60, e.getConfidence()));
                return;
            }

            // ✅ 자동 확정 조건(보수적으로 시작)
            double c = (e.getConfidence() == null) ? 0.0 : e.getConfidence();
            boolean hasCategory = (e.getCategory() != null && !e.getCategory().isBlank());

            if (c >= 0.90 && hasCategory) {
                e.setNeedsUserConfirm(false);
            } else {
                e.setNeedsUserConfirm(true);
            }
        }

        if (e.getType() == EntryType.SCHEDULE) {
            // 시작 시간이 없으면 무조건 확인 필요
            if (e.getStartDateTime() == null) {
                e.setNeedsUserConfirm(true);
                e.setConfidence(Math.min(e.getConfidence(), 0.70));
                return;
            }

            // 시작 시간이 있고, 신뢰도가 높으면 자동 확정(확인 불필요)
            double c = (e.getConfidence() == null) ? 0.0 : e.getConfidence();
            if (c >= 0.90) {
                e.setNeedsUserConfirm(false);
            } else {
                e.setNeedsUserConfirm(true);
            }
        }
    }

    private void normalizeAiResult(Entry e, String rawText) {
        LocalDate today = LocalDate.now();

        // ✅ 상대 날짜 표현이 들어가면 AI가 뭐라 하든 룰 날짜가 최우선
        boolean hasRelative =
                rawText.contains("오늘") ||
                        rawText.contains("내일") ||
                        rawText.contains("모레") ||
                        rawText.matches(".*내일\\s*모레.*");

        if (hasRelative) {
            LocalDate ruleDate = resolveDate(rawText);
            e.setEntryDate(ruleDate);

            // startDateTime이 이미 있으면 날짜만 entryDate에 맞춰 교정
            if (e.getStartDateTime() != null) {
                LocalDateTime s = e.getStartDateTime();
                e.setStartDateTime(ruleDate.atTime(s.getHour(), s.getMinute()));
            }
            if (e.getEndDateTime() != null) {
                LocalDateTime end = e.getEndDateTime();
                e.setEndDateTime(ruleDate.atTime(end.getHour(), end.getMinute()));
            }
        }

        // 1) entryDate가 null이면 룰 기반 resolveDate로 채움
        if (e.getEntryDate() == null) {
            e.setEntryDate(resolveDate(rawText));
        }

        // 2) entryDate가 "오늘 기준 너무 과거"면(예: 2024로 튐) 룰 기반 날짜로 교정
        if (e.getEntryDate() != null && e.getEntryDate().isBefore(today.minusDays(1))) {
            e.setEntryDate(resolveDate(rawText));
        }

        // 3) SCHEDULE인데 startDateTime이 null이면, 룰로 시각을 한 번 더 시도
        if (e.getType() == EntryType.SCHEDULE && e.getStartDateTime() == null) {
            LocalDate date = e.getEntryDate() != null ? e.getEntryDate() : resolveDate(rawText);
            Matcher tm = TIME_PATTERN.matcher(rawText);
            if (tm.find()) {
                int hour = Integer.parseInt(tm.group(1));
                int minute = (tm.group(3) != null) ? Integer.parseInt(tm.group(3)) : 0;
                e.setStartDateTime(date.atTime(hour, minute));
            }
        }

        // 4) startDateTime이 있는데 entryDate와 날짜가 다르면 entryDate 기준으로 교정
        if (e.getStartDateTime() != null && e.getEntryDate() != null) {
            LocalDateTime s = e.getStartDateTime();
            if (!s.toLocalDate().equals(e.getEntryDate())) {
                e.setStartDateTime(e.getEntryDate().atTime(s.getHour(), s.getMinute()));
            }
        }

        // 5) SCHEDULE인데 endDateTime이 없고 start가 있으면 기본 1시간 부여
        if (e.getType() == EntryType.SCHEDULE && e.getStartDateTime() != null && e.getEndDateTime() == null) {
            e.setEndDateTime(e.getStartDateTime().plusHours(1));
        }

        // 6) EXPENSE인데 price가 null/0이면 룰로 한 번 더 추출 시도
        if (e.getType() == EntryType.EXPENSE) {
            if (e.getPrice() == null || e.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal amt = extractAmountFlexible(rawText);
                if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                    e.setPrice(amt);
                }
            }

            // ✅ category가 비어있으면 룰 기반으로 채우기
            if (e.getCategory() == null || e.getCategory().isBlank()) {
                e.setCategory(guessCategory(rawText));
            }

            // (선택) category도 못 맞추면 confidence를 조금 낮춰서 확인 유도
            if (e.getCategory() == null) {
                e.setConfidence(Math.min(e.getConfidence(), 0.75));
            }
            // 지출은 보통 "오늘"이 기본이므로 (원하면) 날짜도 룰 기준으로 고정 가능
            // e.setEntryDate(resolveDate(rawText));
        }

        // 7) location이 null인데 룰로 뽑을 수 있으면 채움
        if (e.getLocation() == null) {
            e.setLocation(guessLocation(rawText));
        }

        // 8) content가 비어있으면 rawText로 복구
        if (e.getContent() == null || e.getContent().isBlank()) {
            e.setContent(rawText);
        }

        // 9) confidence가 비정상이면 범위 보정
        if (e.getConfidence() == null) {
            e.setConfidence(0.65);
        } else {
            double c = e.getConfidence();
            if (c < 0) e.setConfidence(0.0);
            if (c > 1) e.setConfidence(1.0);
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

        // ✅ "내일 모레" 변형 모두 대응 (내일모레, 내일  모레 등)
        if (t.matches(".*내일\\s*모레.*")) return today.plusDays(2);

        if (t.contains("모레")) return today.plusDays(2);
        if (t.contains("내일")) return today.plusDays(1);
        if (t.contains("오늘")) return today;

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


        // 3) "강남역" 같은 '역' 지명
        Pattern p3 = Pattern.compile("([가-힣A-Za-z0-9]+역)");
        Matcher m3 = p3.matcher(t);
        if (m3.find()) return m3.group(1);

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
