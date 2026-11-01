package com.hobozoo.aiqq.service.rag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 火山 Ark 的 OpenAI 兼容流式接口。
 */
@Component
public class ArkChatClient {

    private final LlmProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ArkChatClient(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public record Usage(int tokensIn, int tokensOut) {
    }

    public Usage stream(List<Map<String, String>> messages, Consumer<String> onDelta) throws IOException {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("缺少 rag.llm.api-key，写在 G:/aiqq-rag/config.properties 或环境变量 ARK_API_KEY");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxTokens());
        body.put("stream_options", Map.of("include_usage", true));
        if (properties.thinking() != null && !properties.thinking().isBlank()) {
            body.put("thinking", Map.of("type", properties.thinking()));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型请求被中断", e);
        }
        if (response.statusCode() >= 300) {
            try (InputStream input = response.body()) {
                String error = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("模型接口返回 " + response.statusCode() + "：" + error);
            }
        }

        int tokensIn = 0;
        int tokensOut = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode node = mapper.readTree(data);
                JsonNode usage = node.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    tokensIn = usage.path("prompt_tokens").asInt(tokensIn);
                    tokensOut = usage.path("completion_tokens").asInt(tokensOut);
                }
                JsonNode choices = node.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode content = choices.get(0).path("delta").path("content");
                    if (!content.isMissingNode() && !content.isNull()) {
                        String text = content.asText();
                        if (!text.isEmpty()) {
                            onDelta.accept(text);
                        }
                    }
                }
            }
        }
        return new Usage(tokensIn, tokensOut);
    }
}
