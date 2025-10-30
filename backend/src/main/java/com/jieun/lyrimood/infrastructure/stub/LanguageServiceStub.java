package com.jieun.lyrimood.infrastructure.stub;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import com.jieun.lyrimood.application.port.LanguageService;
import com.jieun.lyrimood.config.ResilientExecutor;
import com.jieun.lyrimood.domain.model.LanguageDetectionResult;

@Service
@ConditionalOnMissingBean(LanguageService.class)
public class LanguageServiceStub implements LanguageService {

    private static final Pattern HANGUL_PATTERN =
            Pattern.compile("[\\u1100-\\u11FF\\u302E\\u302F\\u3130-\\u318F\\uA960-\\uA97F\\uAC00-\\uD7A3\\uD7B0-\\uD7FF]");
    private static final Pattern JAPANESE_PATTERN = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FBF]");
    private static final Pattern SPANISH_PATTERN = Pattern.compile("[ñáéíóúü]");

    private final ResilientExecutor resilientExecutor;

    public LanguageServiceStub(ResilientExecutor resilientExecutor) {
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public LanguageDetectionResult detectLanguage(String text) {
        return resilientExecutor.execute("language", () -> {
            String normalized = normalizeText(text);
            if (normalized.isBlank()) {
                return new LanguageDetectionResult("und", 0.0);
            }

            if (HANGUL_PATTERN.matcher(normalized).find()) {
                return new LanguageDetectionResult("ko", 0.94);
            }
            if (JAPANESE_PATTERN.matcher(normalized).find()) {
                return new LanguageDetectionResult("ja", 0.91);
            }
            if (SPANISH_PATTERN.matcher(normalized).find()) {
                return new LanguageDetectionResult("es", 0.88);
            }
            if (normalized.chars().filter(Character::isLetter).count() == 0) {
                return new LanguageDetectionResult("und", 0.2);
            }

            Locale locale = Locale.forLanguageTag(Locale.getDefault().toLanguageTag());
            return new LanguageDetectionResult(locale.getLanguage(), 0.6);
        });
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            return Normalizer.normalize(text, Normalizer.Form.NFC);
        } catch (Exception ignored) {
            return text;
        }
    }
}
