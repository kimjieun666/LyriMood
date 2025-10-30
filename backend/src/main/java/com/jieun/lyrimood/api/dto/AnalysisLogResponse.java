package com.jieun.lyrimood.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisLogResponse(
        Long id,
        String title,
        String artist,
        String label,
        String lang,
        boolean profane,
        double valence,
        double arousal,
        Integer lyricsLength,
        Double languageConfidence,
        String musicBrainzId,
        java.time.LocalDate releaseDate,
        String releaseCountry,
        List<String> tags,
        List<String> genres,
        String lyrics,
        List<String> highlights,
        String acousticKey,
        Double acousticTempo,
        String acousticMood,
        String lyricsDigest,
        OffsetDateTime createdAt
) {
}
