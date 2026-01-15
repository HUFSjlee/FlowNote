package com.flownote.flownote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiKeyCheck {
    public OpenAiKeyCheck(@Value("${openai.api-key:}") String key) {
        System.out.println("[OpenAI] api-key loaded? length=" + (key == null ? 0 : key.length()));
    }
}
