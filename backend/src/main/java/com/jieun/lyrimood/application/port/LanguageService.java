package com.jieun.lyrimood.application.port;

import com.jieun.lyrimood.domain.model.LanguageDetectionResult;

public interface LanguageService {

    LanguageDetectionResult detectLanguage(String text);
}

