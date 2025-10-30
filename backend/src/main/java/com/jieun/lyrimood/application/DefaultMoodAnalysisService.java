package com.jieun.lyrimood.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jieun.lyrimood.api.dto.MoodAnalyzeRequest;
import com.jieun.lyrimood.api.dto.MoodAnalyzeResponse;
import com.jieun.lyrimood.application.port.LanguageService;
import com.jieun.lyrimood.application.port.AnalysisLogRepository;
import com.jieun.lyrimood.application.port.TagService;
import com.jieun.lyrimood.application.port.MusicMetadataService;
import com.jieun.lyrimood.application.port.AcousticProfileService;
import com.jieun.lyrimood.domain.model.AnalysisLog;
import com.jieun.lyrimood.domain.model.AcousticProfile;
import com.jieun.lyrimood.domain.model.LanguageDetectionResult;
import com.jieun.lyrimood.domain.model.SentimentScore;
import com.jieun.lyrimood.domain.model.MusicMetadata;
import com.jieun.lyrimood.domain.model.AcousticProfile;
import com.jieun.lyrimood.infrastructure.http.GeminiClient;
import com.jieun.lyrimood.shared.ExternalServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultMoodAnalysisService implements MoodAnalysisService {

    private static final String LABEL_SEPARATOR = " \u00B7 ";
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[\\u3131-\\u318E\\uAC00-\\uD7A3]");
    private static final Pattern JAPANESE_PATTERN = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FBF]");
    private static final Pattern CYRILLIC_PATTERN = Pattern.compile("[\\u0400-\\u04FF]");
    private static final List<String> GENRE_KEYWORDS = List.of(
            "pop", "rock", "hip", "ballad", "jazz", "classical", "indie", "folk",
            "metal", "dance", "soul", "rap", "electro", "r&b", "blues", "country",
            "alternative", "k-pop", "j-pop", "c-pop", "edm"
    );

    private static final Logger log = LoggerFactory.getLogger(DefaultMoodAnalysisService.class);

    private final LanguageService languageService;
    private final TagService tagService;
    private final AnalysisLogRepository analysisLogRepository;
    private final MusicMetadataService musicMetadataService;
    private final GeminiClient geminiClient;
    private final AcousticProfileService acousticProfileService;

    public DefaultMoodAnalysisService(LanguageService languageService,
                                      TagService tagService,
                                      AnalysisLogRepository analysisLogRepository,
                                      MusicMetadataService musicMetadataService,
                                      GeminiClient geminiClient,
                                      AcousticProfileService acousticProfileService) {
        this.languageService = languageService;
        this.tagService = tagService;
        this.analysisLogRepository = analysisLogRepository;
        this.musicMetadataService = musicMetadataService;
        this.geminiClient = geminiClient;
        this.acousticProfileService = acousticProfileService;
    }

    @Override
    public MoodAnalyzeResponse analyze(MoodAnalyzeRequest request) {
        try {
            MusicMetadata metadata = musicMetadataService.lookup(request.title(), request.artist()).orElse(null);
            String lyrics = resolveLyrics(request, metadata);

            LanguageDetectionResult languageResult = languageService.detectLanguage(lyrics);

            GeminiClient.Result analysisResult = geminiClient.analyze(
                    request.title(),
                    request.artist(),
                    lyrics
            );

            boolean profane = analysisResult.profane();
            SentimentScore sentimentScore = analysisResult.sentimentScore();

            List<String> tagCandidates = new ArrayList<>();
            if (analysisResult.tags() != null) {
                tagCandidates.addAll(analysisResult.tags());
            }
            List<String> sentimentLabels = buildSentimentLabels(sentimentScore.valence(), sentimentScore.arousal());
            tagCandidates.addAll(sentimentLabels);
            tagCandidates.addAll(tagService.suggestTags(
                    request.title(),
                    request.artist(),
                    lyrics,
                    sentimentScore.valence(),
                    sentimentScore.arousal(),
                    profane,
                    languageResult.languageCode()
            ));

            double roundedValence = roundThreeDecimals(sentimentScore.valence());
            double roundedArousal = roundThreeDecimals(sentimentScore.arousal());
            String label = sentimentLabels.isEmpty()
                    ? buildLabel(roundedValence, roundedArousal)
                    : sentimentLabels.stream()
                            .map(this::capitalize)
                            .limit(4)
                            .collect(java.util.stream.Collectors.joining(" · "));
            if (StringUtils.hasText(analysisResult.label())) {
                tagCandidates.add(analysisResult.label());
            }

            String lang = StringUtils.hasText(analysisResult.lang())
                    ? analysisResult.lang()
                    : languageResult.languageCode();
            String resolvedCountry = resolveCountry(metadata, lyrics, lang);
            if (metadata != null) {
                tagCandidates = enrichWithMetadata(tagCandidates, metadata, resolvedCountry);
            } else if (StringUtils.hasText(resolvedCountry)) {
                tagCandidates = appendCountryTag(tagCandidates, resolvedCountry);
            }
            List<String> genres = extractGenres(metadata, tagCandidates, lang);
            List<String> tags = normalizeTags(tagCandidates, request);
            LocalDate releaseDate = resolveReleaseDate(metadata);

            String acousticKey = null;
            Double acousticTempo = null;
            String acousticMood = null;
            if (metadata != null && StringUtils.hasText(metadata.recordingId())) {
                try {
                    var profileOpt = acousticProfileService.fetchProfile(metadata.recordingId());
                    if (profileOpt.isPresent()) {
                        AcousticProfile profile = profileOpt.get();
                        acousticKey = profile.key();
                        acousticTempo = profile.tempo();
                        acousticMood = profile.mood();
                    }
                } catch (ExternalServiceException ignored) {
                }
            }

            List<String> highlights = buildHighlights(
                    analysisResult,
                    lyrics,
                    profane,
                    roundedValence,
                    roundedArousal,
                    languageResult,
                    releaseDate,
                    resolvedCountry,
                    genres,
                    acousticKey,
                    acousticTempo,
                    acousticMood
            );

            MoodAnalyzeResponse response = new MoodAnalyzeResponse(
                    request.title(),
                    request.artist(),
                    label,
                    lang,
                    profane,
                    roundedValence,
                    roundedArousal,
                    tags,
                    genres,
                    releaseDate,
                    acousticKey,
                    acousticTempo,
                    acousticMood,
                    lyrics,
                    highlights,
                    List.of(),
                    List.of()
            );

            persistLog(response, lyrics, languageResult.confidence(), metadata, resolvedCountry);
            return response;
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("Mood analysis failed", ex);
        }
    }

    private String resolveLyrics(MoodAnalyzeRequest request, MusicMetadata metadata) {
        if (StringUtils.hasText(request.lyrics())) {
            return request.lyrics().trim();
        }
        if (metadata != null && StringUtils.hasText(metadata.annotation())) {
            return metadata.annotation();
        }
        return (request.title() + " " + request.artist()).trim();
    }

    private void persistLog(MoodAnalyzeResponse response,
                             String lyrics,
                             double langConfidence,
                             MusicMetadata metadata,
                             String resolvedCountry) {
        try {
            int length = lyrics != null ? lyrics.length() : 0;
            AnalysisLog log = new AnalysisLog(
                    response.title(),
                    response.artist(),
                    response.label(),
                    response.lang(),
                    response.profane(),
                    response.valence(),
                    response.arousal(),
                    length,
                    langConfidence,
                    metadata != null ? metadata.recordingId() : null,
                    response.releaseDate(),
                    StringUtils.hasText(resolvedCountry)
                            ? resolvedCountry
                            : metadata != null ? metadata.releaseCountry() : null,
                    response.tags(),
                    response.genres(),
                    response.key(),
                    response.tempo(),
                    response.mood(),
                    response.lyrics(),
                    response.highlights(),
                    digestLyrics(lyrics),
                    OffsetDateTime.now()
            );
            analysisLogRepository.save(log);
        } catch (Exception ex) {
            log.warn("Failed to persist analysis log for '{}' - '{}': {}", response.title(), response.artist(), ex.getMessage());
        }
    }

    private List<String> enrichWithMetadata(List<String> original, MusicMetadata metadata, String resolvedCountry) {
        List<String> enriched = new java.util.ArrayList<>(original);
        if (metadata.releaseDate() != null) {
            enriched.add("year-" + metadata.releaseDate().getYear());
        }
        String country = StringUtils.hasText(resolvedCountry)
                ? resolvedCountry
                : metadata.releaseCountry();
        if (StringUtils.hasText(country)) {
            enriched.add(country.toUpperCase(Locale.ROOT));
        }
        if (metadata.tags() != null) {
            metadata.tags().stream()
                    .limit(3)
                    .map(tag -> tag.replace(' ', '-'))
                    .forEach(enriched::add);
        }
        return enriched.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.length() > 24 ? tag.substring(0, 24) : tag)
                .distinct()
                .limit(8)
                .toList();
    }

    private String digestLyrics(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(lyrics.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(lyrics.hashCode());
        }
    }

    private double roundThreeDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String buildLabel(double valence, double arousal) {
        String valenceLabel = valence > 0.5 ? "Bright" : "Dark";
        String arousalLabel = arousal > 0.5 ? "Energetic" : "Calm";

        return valenceLabel + LABEL_SEPARATOR + arousalLabel;
    }

    private List<String> buildHighlights(GeminiClient.Result result,
                                         String lyrics,
                                         boolean profane,
                                         double valence,
                                         double arousal,
                                         LanguageDetectionResult language,
                                         LocalDate releaseDate,
                                         String country,
                                         List<String> genres,
                                         String key,
                                         Double tempo,
                                         String acousticMood) {
        List<String> highlights = new ArrayList<>();

        highlights.add(describeEmotion(valence, arousal));

        if (result.summary() != null && StringUtils.hasText(result.summary())) {
            highlights.add("AI 요약: " + result.summary());
        }

        if (result.positiveEvidence() != null && !result.positiveEvidence().isEmpty()) {
            highlights.add("밝은 분위기를 만든 표현: " + String.join(", ", result.positiveEvidence()));
        }
        if (result.negativeEvidence() != null && !result.negativeEvidence().isEmpty()) {
            highlights.add("분위기를 가라앉힌 표현: " + String.join(", ", result.negativeEvidence()));
        }
        if (profane) {
            highlights.add("비속어가 포함되어 'Explicit'로 분류했습니다.");
        }

        if (language != null && StringUtils.hasText(language.languageCode())) {
            StringBuilder langBuilder = new StringBuilder();
            langBuilder.append("주요 언어는 ").append(language.languageCode().toUpperCase(Locale.ROOT));
            if (language.confidence() > 0) {
                langBuilder.append(String.format(" (신뢰도 %.2f)", language.confidence()));
            }
            langBuilder.append("로 추정됩니다.");
            highlights.add(langBuilder.toString());
        }

        if (genres != null && !genres.isEmpty()) {
            highlights.add("AI가 추정한 장르: " + String.join(", ", genres));
        }

        if (tempo != null || StringUtils.hasText(key) || StringUtils.hasText(acousticMood)) {
            List<String> parts = new ArrayList<>();
            if (tempo != null) {
                parts.add(String.format("템포 %.1f BPM", tempo));
            }
            if (StringUtils.hasText(key)) {
                parts.add("음계 " + key);
            }
            if (StringUtils.hasText(acousticMood)) {
                parts.add("느껴지는 분위기 " + acousticMood);
            }
            if (!parts.isEmpty()) {
                highlights.add("어쿠스틱 프로파일: " + String.join(", ", parts));
            }
        }

        if (releaseDate != null || StringUtils.hasText(country)) {
            StringBuilder releaseInfo = new StringBuilder("발매 정보: ");
            if (releaseDate != null) {
                releaseInfo.append(releaseDate);
            }
            if (StringUtils.hasText(country)) {
                if (releaseDate != null) releaseInfo.append(" · ");
                releaseInfo.append(country.toUpperCase(Locale.ROOT));
            }
            highlights.add(releaseInfo.toString());
        }

        return highlights;
    }

    private String describeEmotion(double valence, double arousal) {
        String tone = valence >= 0.65 ? "매우 밝고" : valence <= 0.35 ? "어두우며" : "중간 밝기로";
        String energy = arousal >= 0.65 ? "에너지가 높은" : arousal <= 0.35 ? "차분한" : "보통 에너지의";
        return String.format("감정 해석: %s %s 분위기 (valence %.3f / arousal %.3f).", tone, energy, valence, arousal);
    }

    private List<String> buildSentimentLabels(double valence, double arousal) {
        List<String> labels = new ArrayList<>();
        if (valence >= 0.65) {
            labels.add("bright");
        } else if (valence <= 0.35) {
            labels.add("somber");
        }
        if (arousal >= 0.65) {
            labels.add("energetic");
        } else if (arousal <= 0.35) {
            labels.add("calm");
        }
        if (valence >= 0.5 && arousal >= 0.5) {
            labels.add("uplifting");
        }
        if (valence <= 0.5 && arousal <= 0.5) {
            labels.add("melancholic");
        }
        if (labels.isEmpty()) {
            labels.add(buildLabel(valence, arousal).toLowerCase(Locale.ROOT));
        }
        return labels.stream().distinct().limit(4).toList();
    }

    private List<String> extractGenres(MusicMetadata metadata,
                                      List<String> tags,
                                      String lang) {
        List<String> genres = new ArrayList<>();
        if (metadata != null && metadata.tags() != null) {
            metadata.tags().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(genres::add);
        }
        if (genres.isEmpty() && tags != null) {
            tags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::toLowerCase)
                    .filter(this::looksLikeGenre)
                    .forEach(genres::add);
        }
        if (genres.isEmpty() && StringUtils.hasText(lang)) {
            switch (lang.toLowerCase(Locale.ROOT)) {
                case "ko" -> genres.add("k-pop");
                case "ja" -> genres.add("j-pop");
                case "zh", "zh-cn", "zh-hans" -> genres.add("c-pop");
                case "es" -> genres.add("latin pop");
                case "en" -> genres.add("pop");
                default -> { }
            }
        }
        return genres.stream()
                .filter(StringUtils::hasText)
                .map(this::formatGenre)
                .distinct()
                .limit(4)
                .toList();
    }

    private String capitalize(String base) {
        if (!StringUtils.hasText(base)) {
            return base;
        }
        String lower = base.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private String formatGenre(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return value;
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(StringUtils::hasText)
                .map(this::capitalize)
                .collect(Collectors.joining(" "));
    }

    private List<String> normalizeTags(List<String> candidates, MoodAnalyzeRequest request) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (String tag : candidates) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
            String key = normalized.toLowerCase(Locale.ROOT);
            map.putIfAbsent(key, normalized);
        }
        String title = StringUtils.hasText(request.title()) ? request.title().toLowerCase(Locale.ROOT) : null;
        String artist = StringUtils.hasText(request.artist()) ? request.artist().toLowerCase(Locale.ROOT) : null;
        return map.values().stream()
                .filter(value -> {
                    String lower = value.toLowerCase(Locale.ROOT);
                    return !lower.equals(title) && !lower.equals(artist);
                })
                .limit(6)
                .toList();
    }

    private LocalDate resolveReleaseDate(MusicMetadata metadata) {
        return metadata != null ? metadata.releaseDate() : null;
    }

    private boolean looksLikeGenre(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        return GENRE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String resolveCountry(MusicMetadata metadata, String lyrics, String lang) {
        if (metadata != null && StringUtils.hasText(metadata.releaseCountry())) {
            return metadata.releaseCountry().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(lang)) {
            String mapped = mapLanguageToCountry(lang);
            if (mapped != null) {
                return mapped;
            }
        }
        if (StringUtils.hasText(lyrics)) {
            if (HANGUL_PATTERN.matcher(lyrics).find()) {
                return "KR";
            }
            if (JAPANESE_PATTERN.matcher(lyrics).find()) {
                return "JP";
            }
            if (CYRILLIC_PATTERN.matcher(lyrics).find()) {
                return "RU";
            }
        }
        return null;
    }

    private String mapLanguageToCountry(String lang) {
        String normalized = lang.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ko" -> "KR";
            case "ja" -> "JP";
            case "zh", "zh-cn", "zh-hans" -> "CN";
            case "zh-tw", "zh-hant" -> "TW";
            case "es" -> "ES";
            case "fr" -> "FR";
            case "de" -> "DE";
            case "it" -> "IT";
            case "ru" -> "RU";
            case "pt" -> "PT";
            case "en" -> "US";
            default -> null;
        };
    }

    private List<String> appendCountryTag(List<String> tags, String country) {
        if (!StringUtils.hasText(country)) {
            return tags;
        }
        List<String> enriched = new ArrayList<>(tags);
        enriched.add(country.toUpperCase(Locale.ROOT));
        return enriched;
    }
}
