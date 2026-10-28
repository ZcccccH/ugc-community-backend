package com.ugccommunity.backend.service;

import com.ugccommunity.backend.entity.SandboxChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {
    private static final int MAX_MESSAGE_LENGTH = 12000;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    @Autowired
    public DeepSeekClient(
            RestTemplateBuilder builder,
            @Value("${app.ai.deepseek.api-key:}") String apiKey,
            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.deepseek.model:deepseek-v4-flash}") String model,
            @Value("${app.ai.deepseek.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${app.ai.deepseek.read-timeout-seconds:60}") int readTimeoutSeconds) {
        this(builder
                        .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                        .build(),
                apiKey, baseUrl, model);
    }

    DeepSeekClient(RestTemplate restTemplate, String apiKey, String baseUrl, String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model == null || model.trim().isEmpty() ? "deepseek-v4-flash" : model.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String generateReply(String mountContext, List<SandboxChatMessage> history) {
        if (!isConfigured()) {
            throw new DeepSeekException("DeepSeek API key is not configured");
        }

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", systemPrompt(mountContext)));
        for (SandboxChatMessage item : history) {
            if (item == null || item.getContent() == null || item.getContent().trim().isEmpty()) continue;
            if (!"user".equals(item.getRole()) && !"assistant".equals(item.getRole())) continue;
            messages.add(message(item.getRole(), truncate(item.getContent().trim())));
        }

        return complete(messages, 0.8, 1200, false);
    }

    /**
     * Generate a reply from the given system prompt and conversation history. The caller builds the full system prompt (world setting, characters, mode, etc.); nothing is appended here.
     */
    public String generateChat(String systemPrompt, List<SandboxChatMessage> history, int maxTokens) {
        return generateChat(systemPrompt, history, maxTokens, 0.8, null);
    }

    /** Same as above, with optional temperature and Top P (Top P is omitted when null or 1) */
    public String generateChat(String systemPrompt, List<SandboxChatMessage> history, int maxTokens, double temperature, Double topP) {
        if (!isConfigured()) {
            throw new DeepSeekException("DeepSeek API key is not configured");
        }
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", systemPrompt == null ? "" : systemPrompt));
        for (SandboxChatMessage item : history) {
            if (item == null || item.getContent() == null || item.getContent().trim().isEmpty()) continue;
            if (!"user".equals(item.getRole()) && !"assistant".equals(item.getRole())) continue;
            messages.add(message(item.getRole(), truncate(item.getContent().trim())));
        }
        return complete(messages, temperature, topP, maxTokens, false);
    }

    /**
     * Ask the model to output a single JSON object (JSON output mode) and return the raw text. The caller parses and validates it;
     * no structure is guaranteed here.
     */
    public String generateJson(String systemPrompt, String userContent, int maxTokens) {
        if (!isConfigured()) {
            throw new DeepSeekException("DeepSeek API key is not configured");
        }
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent));
        return complete(messages, 0.1, maxTokens, true);
    }

    private String complete(List<Map<String, String>> messages, double temperature, int maxTokens, boolean jsonObject) {
        return complete(messages, temperature, null, maxTokens, jsonObject);
    }

    private String complete(List<Map<String, String>> messages, double temperature, Double topP, int maxTokens, boolean jsonObject) {
        Map<String, Object> request = new HashMap<String, Object>();
        if (topP != null && topP < 1.0) request.put("top_p", topP);
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", false);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);
        if (jsonObject) {
            Map<String, String> format = new HashMap<String, String>();
            format.put("type", "json_object");
            request.put("response_format", format);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions",
                    new HttpEntity<Map<String, Object>>(request, headers),
                    JsonNode.class);
            JsonNode body = response.getBody();
            String content = body == null ? "" : body.path("choices").path(0).path("message").path("content").asText("").trim();
            if (!response.getStatusCode().is2xxSuccessful() || content.isEmpty()) {
                throw new DeepSeekException("DeepSeek returned an empty or invalid response");
            }
            return content;
        } catch (DeepSeekException e) {
            throw e;
        } catch (RestClientException e) {
            throw new DeepSeekException("DeepSeek request failed", e);
        }
    }

    private String systemPrompt(String mountContext) {
        return "你是箱庭世界中的剧情对话助手。请严格依据已挂载的世界观、角色和剧本进行自然对话；"
                + "不要声称看到了未提供的设定。用户要求你扮演某个已挂载角色时，请保持角色口吻和设定。\n"
                + "当前挂载设定：" + truncate(mountContext == null ? "暂无挂载内容" : mountContext);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String truncate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.trim().isEmpty() ? "https://api.deepseek.com" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    public static class DeepSeekException extends RuntimeException {
        DeepSeekException(String message) {
            super(message);
        }

        DeepSeekException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
