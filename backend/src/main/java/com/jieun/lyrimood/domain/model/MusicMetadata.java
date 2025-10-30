package com.jieun.lyrimood.domain.model;

import java.time.LocalDate;
import java.util.List;

public record MusicMetadata(
        String recordingId,
        String title,
        String artist,
        LocalDate releaseDate,
        String releaseCountry,
        List<String> tags,
        String annotation
) {

    public String annotation() {
        return annotation != null ? annotation : "";
    }
}
