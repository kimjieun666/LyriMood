package com.jieun.lyrimood.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jieun.lyrimood.application.port.LanguageService;
import com.jieun.lyrimood.config.ExternalServicesProperties;
import com.jieun.lyrimood.config.ResilientExecutor;
import com.jieun.lyrimood.domain.model.LanguageDetectionResult;
import com.jieun.lyrimood.shared.ExternalServiceException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@ConditionalOnProperty(name = "external.detectlanguage.api-key-value")
public class DetectLanguageClient implements LanguageService {

    private final WebClient webClient;
    private final ResilientExecutor executor;
    private final ObjectMapper objectMapper;
    private static final Pattern HANGUL_PATTERN =
            Pattern.compile("[\\u1100-\\u11FF\\u302E\\u302F\\u3130-\\u318F\\uA960-\\uA97F\\uAC00-\\uD7A3\\uD7B0-\\uD7FF]");
    private static final Pattern JAPANESE_PATTERN = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FBF]");
    private static final Pattern SPANISH_PATTERN = Pattern.compile("[ñáéíóúü]");

    public DetectLanguageClient(WebClient.Builder builder,
                                ExternalServicesProperties properties,
                                ResilientExecutor executor,
                                ObjectMapper objectMapper) {
        ExternalServicesProperties.ServiceProperties config = properties.getDetectlanguage();
        this.webClient = builder
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(config.getApiKeyHeader(), "Bearer " + config.getApiKeyValue())
                .build();
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public LanguageDetectionResult detectLanguage(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return new LanguageDetectionResult("und", 0.0);
        }
        return executor.execute("detectlanguage", () -> invoke(normalized));
    }

    private LanguageDetectionResult invoke(String text) {
        try {
            String payload = objectMapper.createObjectNode()
                    .put("q", text)
                    .toString();

            String response = webClient.post()
                    .uri("/detect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(response)) {
                return new LanguageDetectionResult("und", 0.0);
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) {
                return new LanguageDetectionResult("und", 0.0);
            }

            JsonNode first = dataNode.get(0).get("detections");
            if (first == null || !first.isArray()) {
                return fallbackLanguage(text);
            }

            Optional<JsonNode> best = Optional.of(first)
                    .stream()
                    .flatMap(node -> stream(node))
                    .filter(node -> node.hasNonNull("language"))
                    .max(Comparator.comparingDouble(node -> node.path("confidence").asDouble(0.0)));

            if (best.isEmpty()) {
                return fallbackLanguage(text);
            }

            JsonNode chosen = best.get();
            String language = chosen.path("language").asText("und");
            double confidence = chosen.path("confidence").asDouble(0.0);
            if ("und".equals(language)) {
                LanguageDetectionResult fallback = fallbackLanguage(text);
                if (!"und".equals(fallback.languageCode())) {
                    return fallback;
                }
            }
            return new LanguageDetectionResult(language, confidence);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("language detection failed", ex);
        }
    }

    private Stream<JsonNode> stream(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Stream.empty();
        }
        return StreamSupport.stream(arrayNode.spliterator(), false);
    }

    private LanguageDetectionResult fallbackLanguage(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return new LanguageDetectionResult("und", 0.0);
        }
        if (HANGUL_PATTERN.matcher(normalized).find()) {
            return new LanguageDetectionResult("ko", 0.9);
        }
        if (JAPANESE_PATTERN.matcher(normalized).find()) {
            return new LanguageDetectionResult("ja", 0.85);
        }
        if (SPANISH_PATTERN.matcher(normalized).find()) {
            return new LanguageDetectionResult("es", 0.75);
        }
        return new LanguageDetectionResult("und", 0.0);
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            return Normalizer.normalize(text, Normalizer.Form.NFC);
        } catch (Exception ignored) {
            return text;
        }
    }
}
