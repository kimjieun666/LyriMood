package com.jieun.lyrimood.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jieun.lyrimood.api.dto.ModelPredictionDto;
import com.jieun.lyrimood.api.dto.MoodAnalyzeRequest;
import com.jieun.lyrimood.api.dto.MoodAnalyzeResponse;
import com.jieun.lyrimood.application.MoodAnalysisService;
import com.jieun.lyrimood.shared.ExternalServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MoodAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class MoodAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MoodAnalysisService moodAnalysisService;

    @Test
    @DisplayName("POST /api/mood/analyze returns aggregated mood payload")
    void analyze_ReturnsAggregatedResponse() throws Exception {
        var response = new MoodAnalyzeResponse(
                "Song", "Artist", "Bright · Calm",
                "en", false, 0.812, 0.221,
                List.of("Bright", "Calm", "EN"),
                List.of("Pop"),
                null,
                "C Major",
                128.0,
                "happy",
                "우리는 밝은 햇빛 속에서 춤춰요",
                "We dance in the bright daylight",
                List.of("Positive keywords: bright"),
                List.of(new ModelPredictionDto("toxic", 0.15)),
                List.of(new ModelPredictionDto("happiness", 0.64)));

        when(moodAnalysisService.analyze(any(MoodAnalyzeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/mood/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Song",
                                  "artist": "Artist",
                                  "lyrics": "We dance in the bright daylight"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Bright · Calm"))
                .andExpect(jsonPath("$.valence").value(0.812))
                .andExpect(jsonPath("$.tags[0]").value("Bright"))
                .andExpect(jsonPath("$.genres[0]").value("Pop"))
                .andExpect(jsonPath("$.key").value("C Major"))
                .andExpect(jsonPath("$.tempo").value(128.0))
                .andExpect(jsonPath("$.mood").value("happy"))
                .andExpect(jsonPath("$.lyrics").value("우리는 밝은 햇빛 속에서 춤춰요"))
                .andExpect(jsonPath("$.originalLyrics").value("We dance in the bright daylight"))
                .andExpect(jsonPath("$.toxicityPredictions[0].label").value("toxic"));
    }

    @Test
    @DisplayName("External errors map to 502 with message")
    void analyze_ExternalFailure() throws Exception {
        when(moodAnalysisService.analyze(any(MoodAnalyzeRequest.class)))
                .thenThrow(new ExternalServiceException("sentiment unavailable"));

        mockMvc.perform(post("/api/mood/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Song",
                                  "artist": "Artist",
                                  "lyrics": "We dance in the bright daylight"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("sentiment unavailable"));
    }

    @Test
    @DisplayName("Validation errors return 400 with message")
    void analyze_ValidationFailure() throws Exception {
        mockMvc.perform(post("/api/mood/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "artist": "Artist",
                                  "lyrics": "Some lyrics"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
