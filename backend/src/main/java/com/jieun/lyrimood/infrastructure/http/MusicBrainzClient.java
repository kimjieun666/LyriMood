package com.jieun.lyrimood.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jieun.lyrimood.application.port.MusicMetadataService;
import com.jieun.lyrimood.config.ExternalServicesProperties;
import com.jieun.lyrimood.config.ResilientExecutor;
import com.jieun.lyrimood.domain.model.MusicMetadata;
import com.jieun.lyrimood.shared.ExternalServiceException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MusicBrainzClient implements MusicMetadataService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy[-MM[-dd]]");

    private final WebClient webClient;
    private final ResilientExecutor executor;
    private final ObjectMapper objectMapper;

    public MusicBrainzClient(WebClient.Builder builder,
                             ExternalServicesProperties properties,
                             ResilientExecutor executor,
                             ObjectMapper objectMapper) {
        ExternalServicesProperties.ServiceProperties config = properties.getMusicbrainz();
        this.webClient = builder
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "LyriMood/1.0 (https://github.com/kimjieun666/LyriMood)")
                .build();
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MusicMetadata> lookup(String title, String artist) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(artist)) {
            return Optional.empty();
        }
        return executor.execute("musicbrainz", () -> query(title, artist));
    }

    private Optional<MusicMetadata> query(String title, String artist) {
        try {
            String query = buildQuery(title, artist);
            String payload = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ws/2/recording")
                            .queryParam("query", query)
                            .queryParam("fmt", "json")
                            .queryParam("limit", "1")
                            .queryParam("inc", "tags+releases+annotation")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(payload);
            JsonNode recordings = root.get("recordings");
            if (recordings == null || !recordings.isArray() || recordings.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first = recordings.get(0);
            String recordingId = text(first, "id");
            String recordingTitle = text(first, "title");
            String recordingArtist = extractArtist(first);
            LocalDate releaseDate = extractReleaseDate(first);
            String country = extractReleaseCountry(first);
            List<String> tags = extractTags(first);
            String annotation = extractAnnotation(first);

            if (!StringUtils.hasText(annotation) && StringUtils.hasText(recordingId)) {
                annotation = fetchAnnotation(recordingId);
            }

            return Optional.of(new MusicMetadata(
                    recordingId,
                    recordingTitle,
                    recordingArtist,
                    releaseDate,
                    country,
                    tags,
                    annotation
            ));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("music metadata lookup failed", ex);
        }
    }

    private String fetchAnnotation(String recordingId) {
        try {
            String payload = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ws/2/recording/{id}")
                            .queryParam("fmt", "json")
                            .queryParam("inc", "annotation")
                            .build(recordingId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(payload)) {
                return "";
            }

            JsonNode root = objectMapper.readTree(payload);
            return sanitizeAnnotation(root.get("annotation"));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("music metadata annotation lookup failed", ex);
        }
    }

    private List<String> extractTags(JsonNode recording) {
        JsonNode tagNode = recording.get("tags");
        List<String> tags = new ArrayList<>();
        if (tagNode != null && tagNode.isArray()) {
            for (JsonNode node : tagNode) {
                String name = text(node, "name");
                if (StringUtils.hasText(name)) {
                    tags.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return tags;
    }

    private String extractArtist(JsonNode recording) {
        JsonNode credits = recording.get("artist-credit");
        if (credits != null && credits.isArray() && !credits.isEmpty()) {
            JsonNode nameNode = credits.get(0).get("name");
            if (nameNode != null) {
                return nameNode.asText();
            }
        }
        return "";
    }

    private LocalDate extractReleaseDate(JsonNode recording) {
        JsonNode releases = recording.get("releases");
        if (releases != null && releases.isArray()) {
            for (JsonNode release : releases) {
                String dateValue = text(release, "date");
                if (StringUtils.hasText(dateValue)) {
                    return LocalDate.parse(dateValue, DATE_FORMATTER);
                }
            }
        }
        return null;
    }

    private String extractReleaseCountry(JsonNode recording) {
        JsonNode releases = recording.get("releases");
        if (releases != null && releases.isArray()) {
            for (JsonNode release : releases) {
                String country = text(release, "country");
                if (StringUtils.hasText(country)) {
                    return country;
                }
            }
        }
        return null;
    }

    private String extractAnnotation(JsonNode recording) {
        if (recording == null) {
            return "";
        }
        return sanitizeAnnotation(recording.get("annotation"));
    }

    private String sanitizeAnnotation(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isObject()) {
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                return textNode.asText().trim();
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = sanitizeAnnotation(item);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return "";
    }

    private String buildQuery(String title, String artist) {
        return String.format("recording:\"%s\" AND artist:\"%s\"",
                title.replace("\"", ""),
                artist.replace("\"", ""));
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null ? value.asText(null) : null;
    }
}
