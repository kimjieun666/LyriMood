package com.jieun.lyrimood.application;

import com.jieun.lyrimood.api.dto.MoodAnalyzeRequest;
import com.jieun.lyrimood.api.dto.MoodAnalyzeResponse;

public interface MoodAnalysisService {

    MoodAnalyzeResponse analyze(MoodAnalyzeRequest request);
}

