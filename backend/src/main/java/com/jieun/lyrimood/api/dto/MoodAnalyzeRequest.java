package com.jieun.lyrimood.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MoodAnalyzeRequest(
        @NotBlank(message = "title must not be blank")
        String title,
        @NotBlank(message = "artist must not be blank")
        String artist,
        @NotBlank(message = "lyrics must not be blank")
        String lyrics
) {
}
