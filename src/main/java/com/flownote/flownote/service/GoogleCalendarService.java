package com.flownote.flownote.service;

import com.flownote.flownote.entity.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final WebClient webClient = WebClient.create("https://www.googleapis.com/calendar/v3");

    @Value("${google.calendar.id:primary}")
    private String calendarId; // 기본은 primary

    @Value("${google.api.access-token:}")
    private String accessToken;

    private String getAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("google.api.access-token 이 설정되어 있지 않습니다.");
        }
        return accessToken;
    }

    /**
     * 시간 있는 일정(SCHEDULE Entry) → 구글 캘린더 이벤트 생성
     */
    public String createEventForEntry(Entry entry) {

        if (entry.getStartDateTime() == null) {
            throw new IllegalArgumentException("startDateTime 이 없는 일정은 구글 캘린더에 등록할 수 없습니다.");
        }

        // 시간 포맷 (RFC3339)
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        var zoneId = ZoneId.of("Asia/Seoul");

        var startZoned = entry.getStartDateTime().atZone(zoneId);
        var endZoned = (entry.getEndDateTime() != null
                ? entry.getEndDateTime().atZone(zoneId)
                : entry.getStartDateTime().plusHours(1).atZone(zoneId)); // 기본 1시간짜리

        Map<String, Object> body = new HashMap<>();
        body.put("summary", entry.getContent() != null ? entry.getContent() : entry.getRawContent());
        body.put("description", "FlowNote에서 생성된 일정");
        body.put("location", entry.getLocation());

        Map<String, Object> start = new HashMap<>();
        start.put("dateTime", formatter.format(startZoned));
        start.put("timeZone", "Asia/Seoul");

        Map<String, Object> end = new HashMap<>();
        end.put("dateTime", formatter.format(endZoned));
        end.put("timeZone", "Asia/Seoul");

        body.put("start", start);
        body.put("end", end);

        String url = String.format("/calendars/%s/events", calendarId);

        Map<String, Object> response = webClient.post()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(getAccessToken()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.get("id") != null) {
            return response.get("id").toString();
        }

        return null;
    }

    /**
     * 1일짜리 all-day 이벤트 생성 (하루 지출 합계 등)
     */
    public String createAllDayEvent(LocalDate date, String summary, String description) {

        Map<String, Object> body = new HashMap<>();
        body.put("summary", summary);
        body.put("description", description);

        Map<String, Object> start = new HashMap<>();
        start.put("date", date.toString());
        start.put("timeZone", "Asia/Seoul");

        Map<String, Object> end = new HashMap<>();
        // all-day 이벤트에서 end.date 는 exclusive 이라서 +1일
        end.put("date", date.plusDays(1).toString());
        end.put("timeZone", "Asia/Seoul");

        body.put("start", start);
        body.put("end", end);

        String url = String.format("/calendars/%s/events", calendarId);

        Map<String, Object> response = webClient.post()
                .uri(url)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.get("id") != null) {
            return response.get("id").toString();
        }

        return null;
    }

    /**
     * 매월 반복되는 all-day 자동이체 이벤트 생성
     * 예: 매월 25일 카드값 빠져나감
     */
    public String createMonthlyRecurringAllDayEvent(LocalDate firstDate, int dayOfMonth, String summary, String description) {

        Map<String, Object> body = new HashMap<>();
        body.put("summary", summary);
        body.put("description", description);

        Map<String, Object> start = new HashMap<>();
        start.put("date", firstDate.toString());
        start.put("timeZone", "Asia/Seoul");

        Map<String, Object> end = new HashMap<>();
        end.put("date", firstDate.plusDays(1).toString());
        end.put("timeZone", "Asia/Seoul");

        body.put("start", start);
        body.put("end", end);

        // 매월 dayOfMonth일 반복
        String rrule = "RRULE:FREQ=MONTHLY;BYMONTHDAY=" + dayOfMonth;
        body.put("recurrence", List.of(rrule));

        String url = String.format("/calendars/%s/events", calendarId);

        Map<String, Object> response = webClient.post()
                .uri(url)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.get("id") != null) {
            return response.get("id").toString();
        }

        return null;
    }

    /**
     * 기존 all-day 이벤트 수정 (하루 지출 합계 변경 등)
     */
    public void updateAllDayEvent(String eventId, LocalDate date, String summary, String description) {

        Map<String, Object> body = new HashMap<>();
        body.put("summary", summary);
        body.put("description", description);

        Map<String, Object> start = new HashMap<>();
        start.put("date", date.toString());
        start.put("timeZone", "Asia/Seoul");

        Map<String, Object> end = new HashMap<>();
        end.put("date", date.plusDays(1).toString());
        end.put("timeZone", "Asia/Seoul");

        body.put("start", start);
        body.put("end", end);

        String url = String.format("/calendars/%s/events/%s", calendarId, eventId);

        webClient.patch()
                .uri(url)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
