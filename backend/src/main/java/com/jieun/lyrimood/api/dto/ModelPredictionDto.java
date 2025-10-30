package com.jieun.lyrimood.api.dto;

public record ModelPredictionDto(
        String label,
        double score
) {
}
