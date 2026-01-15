package com.flownote.flownote.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flownote.flownote.config.OpenAiProperties;
import com.flownote.flownote.dto.AiParsedEntryResult;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiParsingService {

    private final WebClient openAiWebClient;
    private final OpenAiProperties props;
    private final ObjectMapper om;

    public OpenAiParsingService(WebClient openAiWebClient, OpenAiProperties props, ObjectMapper objectMapper) {
        this.openAiWebClient = openAiWebClient;
        this.props = props;
        this.om = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AiParsedEntryResult parse(String text) {
        String today = java.time.LocalDate.now().toString(); // 예: 2026-01-14

        System.out.println("[OpenAI] request text = " + text);
        if (props.apikey() == null || props.apikey().isBlank()) return null;

        String system = ("""
            너는 사용자의 한 줄 입력을 분석해 일정/지출/메모로 분류하고,
            아래 JSON만 출력한다. JSON 외 텍스트는 절대 출력하지 마라.

            기준 날짜(today)는 %s 이다.
            - "오늘"은 today
            - "내일"은 today+1일
            - "모레"는 today+2일
            - 사용자가 날짜를 말하지 않으면 entryDate는 today로 설정한다.
            - startDateTime/endDateTime에 날짜가 필요하면 entryDate를 사용해 날짜를 채운다.
            - 절대 임의의 과거/미래 날짜를 만들어내지 마라.

            JSON 스키마:
            {
              "type": "EXPENSE|SCHEDULE|NOTE",
              "entryDate": "YYYY-MM-DD 또는 null",
              "content": "정제된 내용(짧게)",
              "price": 0 또는 null,
              "category": "string 또는 null",
              "startDateTime": "YYYY-MM-DDTHH:mm 또는 null",
              "endDateTime": "YYYY-MM-DDTHH:mm 또는 null",
              "location": "string 또는 null",
              "confidence": 0.0~1.0
            }

            규칙:
            - 금액이 있으면 EXPENSE 우선
            - 일정은 날짜/시간/장소를 최대한 추출
            - endDateTime을 모르면 null로 둬라
            - 모르는 값은 null로 둬라
            """).formatted(today);


        Map<String, Object> body = Map.of(
                "model", props.model(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", text)
                )
        );

        String content = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .map(msg -> new RuntimeException("OpenAI error: " + resp.statusCode() + " body=" + msg))
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(12))
                .map(this::extractContent)
                .block();

        if (content == null) return null;

        System.out.println("[OpenAI] raw response = " + content);
        // 혹시 ```json ... ``` 로 감싸서 오면 제거
        String json = stripCodeFence(content);

        try {
            return om.readValue(json, AiParsedEntryResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractContent(Map<?, ?> res) {
        try {
            List<?> choices = (List<?>) res.get("choices");
            Map<?, ?> first = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) first.get("message");
            Object c = message.get("content");
            return c == null ? null : c.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            // ```json\n...\n``` 형태 제거
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
