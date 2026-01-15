package com.flownote.flownote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apikey,
        String baseurl,
        String model
) {}
