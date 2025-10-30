package com.jieun.lyrimood.api.dto;

import java.util.List;

public record MoodAnalyzeResponse(
        String title,
        String artist,
        String label,
        String lang,
        boolean profane,
        double valence,
        double arousal,
        List<String> tags,
        List<String> genres,
        java.time.LocalDate releaseDate,
        String key,
        Double tempo,
        String mood,
        String lyrics,
        String originalLyrics,
        List<String> highlights,
        List<ModelPredictionDto> toxicityPredictions,
        List<ModelPredictionDto> emotionPredictions
) {
}
