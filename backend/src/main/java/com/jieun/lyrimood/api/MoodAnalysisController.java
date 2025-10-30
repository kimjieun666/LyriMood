package com.jieun.lyrimood.api;

import com.jieun.lyrimood.api.dto.MoodAnalyzeRequest;
import com.jieun.lyrimood.api.dto.MoodAnalyzeResponse;
import com.jieun.lyrimood.application.MoodAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mood")
public class MoodAnalysisController {

    private final MoodAnalysisService moodAnalysisService;

    public MoodAnalysisController(MoodAnalysisService moodAnalysisService) {
        this.moodAnalysisService = moodAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<MoodAnalyzeResponse> analyze(@Valid @RequestBody MoodAnalyzeRequest request) {
        MoodAnalyzeResponse response = moodAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}

