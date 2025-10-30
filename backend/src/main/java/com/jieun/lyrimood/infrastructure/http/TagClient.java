package com.jieun.lyrimood.infrastructure.http;

import com.jieun.lyrimood.application.port.TagService;
import com.jieun.lyrimood.infrastructure.support.tag.LocalKeyphraseExtractor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
public class TagClient implements TagService {

    private static final int MAX_TAGS = 8;

    @Override
    public List<String> suggestTags(String title,
                                    String artist,
                                    String lyrics,
                                    double valence,
                                    double arousal,
                                    boolean profane,
                                    String languageCode) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add(valence > 0.5 ? "Bright" : "Dark");
        tags.add(arousal > 0.5 ? "Energetic" : "Calm");
        tags.add(StringUtils.hasText(languageCode) ? languageCode.toUpperCase(Locale.ROOT) : "UND");
        tags.add(profane ? "Explicit" : "Clean");

        tags.addAll(LocalKeyphraseExtractor.extract(title, artist, lyrics, 8));

        while (tags.size() < 3) {
            tags.add("Mood");
            if (tags.size() == 3) {
                break;
            }
            tags.add("Atmospheric");
        }

        List<String> normalized = tags.stream()
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .map(TagClient::truncate)
                .limit(MAX_TAGS)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        while (normalized.size() < 3) {
            normalized.add("mood");
        }
        return List.copyOf(normalized);
    }

    private static String truncate(String value) {
        if (value.length() <= 32) {
            return value;
        }
        return value.substring(0, 32);
    }
}

