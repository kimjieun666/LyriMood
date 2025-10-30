package com.jieun.lyrimood.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jieun.lyrimood.application.port.AcousticProfileService;
import com.jieun.lyrimood.config.ResilientExecutor;
import com.jieun.lyrimood.domain.model.AcousticProfile;
import com.jieun.lyrimood.shared.ExternalServiceException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AcousticBrainzClient implements AcousticProfileService {

    private final WebClient webClient;
    private final ResilientExecutor executor;
    private final ObjectMapper objectMapper;

    public AcousticBrainzClient(WebClient.Builder builder,
                                ResilientExecutor executor,
                                ObjectMapper objectMapper,
                                @Value("${external.acousticbrainz.base-url:https://acousticbrainz.org}") String baseUrl) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AcousticProfile> fetchProfile(String musicBrainzId) {
        if (!StringUtils.hasText(musicBrainzId)) {
            return Optional.empty();
        }
        return executor.execute("acousticbrainz", () -> query(musicBrainzId));
    }

    private Optional<AcousticProfile> query(String mbid) {
        try {
            String payload = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{mbid}/high-level")
                            .build(mbid))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(payload);

            Double tempo = null;
            JsonNode rhythmic = root.path("rhythm").path("bpm");
            if (rhythmic.isObject()) {
                JsonNode valueNode = rhythmic.path("value");
                if (valueNode.isNumber()) {
                    tempo = valueNode.asDouble();
                }
            } else if (rhythmic.isNumber()) {
                tempo = rhythmic.asDouble();
            }

            String key = null;
            JsonNode tonal = root.path("tonal").path("key_key");
            if (tonal.isObject()) {
                JsonNode valueNode = tonal.path("value");
                if (valueNode.isTextual()) {
                    key = valueNode.asText();
                }
            } else if (tonal.isTextual()) {
                key = tonal.asText();
            }

            String mood = extractMood(root.path("mood"));

            if (key == null && tempo == null && mood == null) {
                return Optional.empty();
            }
            return Optional.of(new AcousticProfile(key, tempo, mood));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("acoustic profile lookup failed", ex);
        }
    }

    private String extractMood(JsonNode moodNode) {
        if (moodNode == null || moodNode.isNull()) {
            return null;
        }
        // AcousticBrainz high-level contains multiple mood classifiers, choose one with highest probability
        String[] candidates = {"mood_happy", "mood_sad", "mood_party", "mood_relaxed", "mood_aggressive"};
        double bestScore = 0.0;
        String bestLabel = null;
        for (String candidate : candidates) {
            JsonNode candidateNode = moodNode.path(candidate);
            JsonNode probNode = candidateNode.path("probability");
            JsonNode valueNode = candidateNode.path("value");
            double probability = probNode.isNumber() ? probNode.asDouble() : 0.0;
            String value = valueNode.isTextual() ? valueNode.asText() : null;
            if (probability > bestScore && StringUtils.hasText(value)) {
                bestScore = probability;
                bestLabel = value;
            }
        }
        if (!StringUtils.hasText(bestLabel)) {
            JsonNode valueNode = moodNode.path("value");
            if (valueNode.isTextual()) {
                bestLabel = valueNode.asText();
            }
        }
        return bestLabel;
    }
}
