package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.exception.OpenAiException;
import com.csee.swplus.mileage.portfolio.exception.OpenAiNotConfiguredException;
import com.csee.swplus.mileage.portfolio.exception.OpenAiTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin OpenAI Chat Completions wrapper for CV HTML generation.
 * <p>
 * No reactive / SDK dependency — uses {@link RestTemplate} configured with read/connect timeouts from
 * {@code openai.api.timeout-seconds}. Returns structured {@link Result} (html + token usage + model name)
 * so the service layer can persist cost-tracking metadata on the CV row.
 * <p>
 * Errors are surfaced as {@link OpenAiException} subclasses for the controller-advice layer to map to
 * HTTP 502/503/504. The OpenAI response often wraps HTML in {@code ```html ... ```} fences; this client
 * strips them before returning.
 */
@Component
@Slf4j
public class OpenAiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Matches an entire fenced code block (optionally with language tag). */
    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "(?s)^\\s*```(?:[a-zA-Z0-9_+-]*)\\s*\\n(.*?)\\n```\\s*$");

    /**
     * Anchored guard rails so the model returns an HTML document only — no commentary, no fences.
     * STEP 1 of the new template already says "ONE-SHOT", so this just reinforces output shape.
     */
    private static final String SYSTEM_PROMPT =
            "You are an HTML generator. Return EXACTLY ONE complete HTML document and nothing else. "
                    + "Do NOT wrap the output in markdown code fences. Do NOT include any prose, explanations, "
                    + "or chat preamble before or after the HTML. The first character of your response MUST be "
                    + "'<' and the last must be '>'.";

    private final String apiKey;
    private final String defaultModel;
    private final String baseUrl;
    private final int maxOutputTokens;
    private final double temperature;
    private final RestTemplate restTemplate;

    public OpenAiClient(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.model:gpt-4o}") String defaultModel,
            @Value("${openai.api.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.api.timeout-seconds:90}") int timeoutSeconds,
            @Value("${openai.api.max-output-tokens:12000}") int maxOutputTokens,
            @Value("${openai.api.temperature:0.3}") double temperature,
            RestTemplateBuilder restTemplateBuilder) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty()
                ? baseUrl.replaceAll("/+$", "")
                : "https://api.openai.com/v1";
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        Duration timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(timeout)
                .build();
    }

    /** True iff a non-blank API key is configured. */
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /** Convenience: uses {@link #defaultModel}. */
    public Result generateHtml(String prompt) {
        return generateHtml(prompt, null);
    }

    /**
     * Calls Chat Completions with the prompt as a single user message and returns the assistant's HTML.
     *
     * @param prompt        the full LLM prompt (already includes ROLE/TASK/STEP 1-7 + injected data)
     * @param modelOverride optional model name; falls back to {@link #defaultModel}
     */
    public Result generateHtml(String prompt, String modelOverride) {
        if (!isConfigured()) {
            throw new OpenAiNotConfiguredException(
                    "OPENAI_API_KEY is not set. Configure the openai.api.key property or env var.");
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new OpenAiException("Prompt is empty — cannot call OpenAI.");
        }

        String model = (modelOverride != null && !modelOverride.trim().isEmpty())
                ? modelOverride.trim()
                : defaultModel;

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxOutputTokens);
        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);

        String url = baseUrl + "/chat/completions";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body.toString(), headers), String.class);
            return parseResponse(response.getBody(), model);
        } catch (HttpStatusCodeException e) {
            int status = e.getRawStatusCode();
            String snippet = truncate(e.getResponseBodyAsString(StandardCharsets.UTF_8), 600);
            log.warn("OpenAI HTTP {} for model={}: {}", status, model, snippet);
            throw new OpenAiException(
                    "OpenAI returned HTTP " + status + ": " + snippet);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new OpenAiTimeoutException(
                        "OpenAI request timed out (>" + restTemplate + "). Try shorter prompt or raise OPENAI_TIMEOUT.",
                        e);
            }
            throw new OpenAiException("OpenAI network error: " + e.getMessage(), e);
        } catch (OpenAiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OpenAiException("Unexpected OpenAI client error: " + e.getMessage(), e);
        }
    }

    private Result parseResponse(String body, String model) {
        if (body == null || body.isEmpty()) {
            throw new OpenAiException("OpenAI returned an empty response body.");
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new OpenAiException(
                        "OpenAI response missing choices: " + truncate(body, 400));
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isEmpty()) {
                throw new OpenAiException(
                        "OpenAI response choices[0].message.content was empty.");
            }
            String html = stripCodeFence(content).trim();
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            int totalTokens = root.path("usage").path("total_tokens").asInt(promptTokens + completionTokens);
            return new Result(html, model, promptTokens, completionTokens, totalTokens);
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException(
                    "Failed to parse OpenAI response: " + truncate(body, 400), e);
        }
    }

    /** Strip a single outer ```...``` fence the model may add despite the system prompt. */
    static String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        Matcher m = FENCED_BLOCK.matcher(content);
        if (m.matches()) {
            return m.group(1);
        }
        return content;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(truncated)";
    }

    /** Generation result carrying token usage so the service can persist cost-tracking fields. */
    @Getter
    public static class Result {
        private final String html;
        private final String model;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public Result(String html, String model, int promptTokens, int completionTokens, int totalTokens) {
            this.html = html;
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
    }
}
