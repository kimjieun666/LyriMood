package com.jieun.lyrimood.infrastructure.support;

import java.util.Map;

public final class SentimentMath {

    private SentimentMath() {
    }

    public static double calculateValence(Map<String, Double> distribution) {
        double happiness = distribution.getOrDefault("happiness", distribution.getOrDefault("joy", 0.0));
        double disgust = distribution.getOrDefault("disgust", distribution.getOrDefault("neutral", 0.0));

        return 0.90 * happiness
                + 0.55 * distribution.getOrDefault("surprise", 0.0)
                - 0.85 * distribution.getOrDefault("sadness", 0.0)
                - 0.80 * distribution.getOrDefault("anger", 0.0)
                - 0.75 * distribution.getOrDefault("fear", 0.0)
                - 0.80 * disgust;
    }

    public static double calculateArousal(Map<String, Double> distribution) {
        double happiness = distribution.getOrDefault("happiness", distribution.getOrDefault("joy", 0.0));
        double disgust = distribution.getOrDefault("disgust", distribution.getOrDefault("neutral", 0.0));

        return 0.82 * distribution.getOrDefault("anger", 0.0)
                + 0.78 * distribution.getOrDefault("fear", 0.0)
                + 0.70 * distribution.getOrDefault("surprise", 0.0)
                + 0.55 * happiness
                - 0.40 * distribution.getOrDefault("sadness", 0.0)
                - 0.35 * disgust;
    }

    public static double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
