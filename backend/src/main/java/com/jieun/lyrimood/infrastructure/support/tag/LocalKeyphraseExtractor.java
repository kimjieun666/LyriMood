package com.jieun.lyrimood.infrastructure.support.tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class LocalKeyphraseExtractor {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "you", "with", "from", "that", "this", "have", "into", "they", "your",
            "about", "over", "under", "when", "where", "there", "their", "ours", "like", "just",
            "cause", "never", "ever", "again", "chorus", "verse", "repeat", "한", "그", "저", "우리",
            "너", "나는", "그리고", "그래도", "하지만", "지난", "오늘", "내일"
    );

    private LocalKeyphraseExtractor() {
    }

    public static List<String> extract(String title, String artist, String lyrics, int limit) {
        String aggregated = StreamComposer.compose(title, artist, lyrics);
        if (!StringUtils.hasText(aggregated)) {
            return List.of();
        }

        Map<String, AtomicInteger> frequencies = new LinkedHashMap<>();
        for (String token : TOKEN_SPLIT.split(aggregated.toLowerCase(Locale.ROOT))) {
            if (!isValidToken(token)) {
                continue;
            }
            frequencies.computeIfAbsent(token, key -> new AtomicInteger()).incrementAndGet();
        }

        return frequencies.entrySet().stream()
                .sorted((l, r) -> {
                    int freq = Integer.compare(r.getValue().get(), l.getValue().get());
                    if (freq != 0) {
                        return freq;
                    }
                    return l.getKey().compareTo(r.getKey());
                })
                .limit(Math.max(0, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean isValidToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (token.length() <= 2) {
            return false;
        }
        return !STOP_WORDS.contains(token);
    }

    static final class StreamComposer {
        private StreamComposer() {
        }

        static String compose(String title, String artist, String lyrics) {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.hasText(title)) {
                builder.append(title).append(' ');
            }
            if (StringUtils.hasText(artist)) {
                builder.append(artist).append(' ');
            }
            if (StringUtils.hasText(lyrics)) {
                builder.append(lyrics);
            }
            return builder.toString();
        }
    }
}

