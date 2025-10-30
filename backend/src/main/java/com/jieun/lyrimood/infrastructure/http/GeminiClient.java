package com.jieun.lyrimood.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jieun.lyrimood.config.ExternalServicesProperties;
import com.jieun.lyrimood.config.ResilientExecutor;
import com.jieun.lyrimood.domain.model.SentimentScore;
import com.jieun.lyrimood.shared.ExternalServiceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiClient {

    private final WebClient webClient;
    private final ResilientExecutor executor;
    private final ObjectMapper objectMapper;
    private final ExternalServicesProperties.ServiceProperties properties;
    private final String modelName;

    public GeminiClient(WebClient.Builder builder,
                        ExternalServicesProperties properties,
                        ResilientExecutor executor,
                        ObjectMapper objectMapper) {
        this.properties = properties.getGemini();
        this.webClient = builder
                .baseUrl(resolveBaseUrl(this.properties))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.modelName = StringUtils.hasText(this.properties.getModel())
                ? this.properties.getModel()
                : "gemini-2.5-flash";
    }

    public Result analyze(String title, String artist, String lyrics) {
        return executor.execute("gemini", () -> invoke(title, artist, lyrics));
    }

    private Result invoke(String title, String artist, String lyrics) {
        if (!StringUtils.hasText(properties.getApiKeyValue())) {
            throw new ExternalServiceException("Gemini API key is not configured");
        }
        if (!StringUtils.hasText(lyrics)) {
            return defaultResult();
        }

        try {
            String payload = objectMapper.writeValueAsString(buildRequest(title, artist, lyrics));
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", properties.getApiKeyValue())
                            .build(modelName))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(response)) {
                return defaultResult();
            }
            return parseResponse(response).orElseGet(this::defaultResult);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("gemini analysis failed", ex);
        }
    }

    private Map<String, Object> buildRequest(String title, String artist, String lyrics) {
        String subject = String.format("제목: %s%n아티스트: %s", title, artist);
        String prompt = """
                당신은 음악의 감정과 분위기를 분석하는 전문가입니다. 아래 가사를 한국어로 분석하고, **JSON 한 줄**만 반환하세요.
                JSON은 반드시 다음 필드를 포함한 1줄(minified)이어야 합니다.
                {
                  "label": string,           // 간결한 한국어 감정 레이블
                  "valence": number,         // 0.0 ~ 1.0
                  "arousal": number,         // 0.0 ~ 1.0
                  "profane": boolean,        // 비속어 포함 여부
                  "tags": array,             // 소문자 영어 태그 최대 6개
                  "positiveEvidence": array, // 밝은 분위기를 보여주는 한국어 표현 최대 3개
                  "negativeEvidence": array, // 어두운 분위기를 보여주는 한국어 표현 최대 3개
                  "summary": string,         // 120자 이하 한국어 요약
                  "language": string,        // ISO 639-1 언어 코드
                  "translatedLyrics": string // 가사를 자연스러운 한국어로 번역, 줄바꿈은 \\n 사용, 4000자 이하
                }
                값이 없으면 valence/arousal은 0.5, 배열은 []로 두고 JSON 이외의 텍스트는 출력하지 마세요.

                """ + subject + "\n가사:\n" + lyrics;

        return Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                )
        );
    }

    private Optional<Result> parseResponse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            if (!textNode.isTextual()) {
                return Optional.empty();
            }

            String json = extractJson(textNode.asText());
            JsonNode data = objectMapper.readTree(json);

            double valence = clamp(data.path("valence").asDouble(0.5));
            double arousal = clamp(data.path("arousal").asDouble(0.5));
            SentimentScore score = new SentimentScore(valence, arousal);

            String label = data.path("label").asText("neutral");
            boolean profane = data.path("profane").asBoolean(false);
            String summary = data.path("summary").asText("");
            String lang = data.path("language").asText("");
            String translatedLyrics = data.path("translatedLyrics").asText("");

            List<String> tags = readArray(data.path("tags"));
            List<String> positives = readArray(data.path("positiveEvidence"));
            List<String> negatives = readArray(data.path("negativeEvidence"));

            return Optional.of(
                    new Result(
                            lang,
                            label,
                            profane,
                            score,
                            tags,
                            positives,
                            negatives,
                            summary,
                            translatedLyrics
                    )
            );
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private List<String> readArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            String value = node.asText();
            return StringUtils.hasText(value) ? List.of(value.trim()) : List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(8)
                .toList();
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Result defaultResult() {
        return new Result(
                "",
                "neutral",
                false,
                new SentimentScore(0.5, 0.5),
                List.of(),
                List.of(),
                List.of(),
                "",
                ""
        );
    }

    private String resolveBaseUrl(ExternalServicesProperties.ServiceProperties props) {
        if (props == null || !StringUtils.hasText(props.getBaseUrl())) {
            return "https://generativelanguage.googleapis.com";
        }
        return props.getBaseUrl();
    }

    public record Result(
            String lang,
            String label,
            boolean profane,
            SentimentScore sentimentScore,
            List<String> tags,
            List<String> positiveEvidence,
            List<String> negativeEvidence,
            String summary,
            String translatedLyrics
    ) {
    }
}
