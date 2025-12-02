package com.flownote.flownote.service;

import com.flownote.flownote.entity.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final WebClient webClient = WebClient.create("https://www.googleapis.com/calendar/v3");

    @Value("${google.calendar.id:primary}")
    private String calendarId; // 기본은 primary

    //application.yml 에서 access token 읽어오기
    @Value("${google.api.access-token}")
    private String accessToken;

    //예외 던지던 메서드를 실제 토큰 반환 메서드로 변경
    private String getAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("google.api.access-token 이 설정되어 있지 않습니다.");
        }
        return accessToken;
    }

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
        body.put("description", "FlowNote에서 생성됨");
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

        // blocking 방식으로 간단히
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
}
