package com.jieun.lyrimood.infrastructure.stub;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import com.jieun.lyrimood.application.port.TagService;
import com.jieun.lyrimood.config.ResilientExecutor;

@Service
@ConditionalOnMissingBean(TagService.class)
public class TagServiceStub implements TagService {

    private static final Map<String, String> KEYWORD_TO_TAG = Map.ofEntries(
            Map.entry("love", "Romantic"),
            Map.entry("heart", "Heartfelt"),
            Map.entry("midnight", "Nocturnal"),
            Map.entry("rain", "Moody"),
            Map.entry("summer", "Summer"),
            Map.entry("불꽃", "Intense"),
            Map.entry("기억", "Nostalgic"),
            Map.entry("춤", "Dance"),
            Map.entry("해변", "Beachy")
    );

    private final ResilientExecutor resilientExecutor;

    public TagServiceStub(ResilientExecutor resilientExecutor) {
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public java.util.List<String> suggestTags(String title, String artist, String lyrics, double valence, double arousal, boolean profane, String languageCode) {
        return resilientExecutor.execute("tag", () -> {
            String combined = Stream.of(title, artist, lyrics)
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .reduce("", (left, right) -> left + " " + right);

            LinkedHashSet<String> tags = new LinkedHashSet<>();
            tags.add(describeValence(valence));
            tags.add(describeArousal(arousal));
            tags.add(languageCode == null || languageCode.isBlank()
                    ? "Unknown-Lang"
                    : languageCode.toUpperCase(Locale.ROOT));
            tags.add(profane ? "Explicit" : "Clean");

            KEYWORD_TO_TAG.forEach((keyword, tag) -> {
                if (combined.contains(keyword.toLowerCase(Locale.ROOT))) {
                    tags.add(tag);
                }
            });

            if (tags.size() < 3) {
                tags.add("Atmospheric");
            }
            while (tags.size() < 3) {
                tags.add("Mood");
            }

            return tags.stream()
                    .limit(8)
                    .toList();
        });
    }

    private String describeValence(double valence) {
        if (valence >= 0.65) {
            return "Bright";
        }
        if (valence <= 0.35) {
            return "Somber";
        }
        return "Warm";
    }

    private String describeArousal(double arousal) {
        if (arousal >= 0.65) {
            return "Energetic";
        }
        if (arousal <= 0.35) {
            return "Calm";
        }
        return "Steady";
    }
}
