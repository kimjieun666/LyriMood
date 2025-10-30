package com.jieun.lyrimood.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jieun.lyrimood.api.dto.MoodAnalyzeRequest;
import com.jieun.lyrimood.application.port.LanguageService;
import com.jieun.lyrimood.application.port.AnalysisLogRepository;
import com.jieun.lyrimood.application.port.MusicMetadataService;
import com.jieun.lyrimood.application.port.AcousticProfileService;
import com.jieun.lyrimood.application.port.TagService;
import com.jieun.lyrimood.domain.model.LanguageDetectionResult;
import com.jieun.lyrimood.domain.model.SentimentScore;
import com.jieun.lyrimood.domain.model.MusicMetadata;
import com.jieun.lyrimood.infrastructure.http.LyricsAnalyzerClient;
import com.jieun.lyrimood.infrastructure.http.LyricsAnalyzerClient.Result;
import com.jieun.lyrimood.shared.ExternalServiceException;

@ExtendWith(MockitoExtension.class)
class DefaultMoodAnalysisServiceTest {

    @Mock
    private LanguageService languageService;
    @Mock
    private TagService tagService;
    @Mock
    private AnalysisLogRepository analysisLogRepository;
    @Mock
    private MusicMetadataService musicMetadataService;
    @Mock
    private LyricsAnalyzerClient lyricsAnalyzerClient;
    @Mock
    private AcousticProfileService acousticProfileService;

    @InjectMocks
    private DefaultMoodAnalysisService service;

    private MoodAnalyzeRequest request;
    private String annotation;

    @BeforeEach
    void setUp() {
        annotation = "We dance in the bright daylight";
        request = new MoodAnalyzeRequest("Shining Day", "Lumi", annotation);
    }

    @Test
    void analyze_ShouldComposeResponseAndRoundScores() {
        when(musicMetadataService.lookup(request.title(), request.artist()))
                .thenReturn(Optional.of(new MusicMetadata("mbid", request.title(), request.artist(),
                        java.time.LocalDate.of(2016, 9, 1), "US", List.of("electropop"), annotation)));
        when(acousticProfileService.fetchProfile("mbid")).thenReturn(Optional.empty());
        when(languageService.detectLanguage(annotation))
                .thenReturn(new LanguageDetectionResult("en", 0.9));
        when(lyricsAnalyzerClient.analyze(request.title(), request.artist(), annotation)).thenReturn(new Result(
                "en",
                "",
                false,
                new SentimentScore(0.81256, 0.3324),
                List.of("energetic", "bright"),
                List.of("bright"),
                List.of()
        ));
        when(tagService.suggestTags(request.title(), request.artist(), annotation, 0.81256, 0.3324, false, "en"))
                .thenReturn(List.of("Bright", "Calm", "EN", "Clean"));

        var response = service.analyze(request);

        assertThat(response.title()).isEqualTo("Shining Day");
        assertThat(response.label()).isEqualTo("Bright · Calm");
        assertThat(response.valence()).isEqualTo(0.813);
        assertThat(response.arousal()).isEqualTo(0.332);
        assertThat(response.tags()).contains("year-2016", "energetic");
        assertThat(response.tags()).hasSizeLessThanOrEqualTo(6);
        assertThat(response.profane()).isFalse();
        assertThat(response.lang()).isEqualTo("en");
        assertThat(response.genres()).contains("Electropop");
        assertThat(response.releaseDate()).isEqualTo(LocalDate.of(2016, 9, 1));
        assertThat(response.key()).isNull();
        assertThat(response.tempo()).isNull();
        assertThat(response.mood()).isNull();
        assertThat(response.toxicityPredictions()).isEmpty();
        assertThat(response.emotionPredictions()).isEmpty();
        assertThat(response.lyrics()).isEqualTo(annotation);
        assertThat(response.highlights()).isNotEmpty();
    }

    @Test
    void analyze_ShouldWrapUnexpectedRuntimeException() {
        when(musicMetadataService.lookup(request.title(), request.artist()))
                .thenReturn(Optional.of(new MusicMetadata("mbid", request.title(), request.artist(),
                        null, null, List.of(), annotation)));
        when(languageService.detectLanguage(annotation))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Mood analysis failed");
    }

    @Test
    void analyze_ShouldUseMetadataWhenAnnotationMissing() {
        String fallback = "Shining Day Lumi";
        var requestWithoutLyrics = new MoodAnalyzeRequest("Shining Day", "Lumi", " ");
        when(musicMetadataService.lookup(requestWithoutLyrics.title(), requestWithoutLyrics.artist()))
                .thenReturn(Optional.empty());
        when(languageService.detectLanguage(fallback))
                .thenReturn(new LanguageDetectionResult("en", 0.8));
        when(lyricsAnalyzerClient.analyze(requestWithoutLyrics.title(), requestWithoutLyrics.artist(), fallback)).thenReturn(new Result(
                "en",
                "",
                false,
                new SentimentScore(0.4, 0.6),
                List.of(),
                List.of(),
                List.of()
        ));
        when(tagService.suggestTags(requestWithoutLyrics.title(), requestWithoutLyrics.artist(), fallback, 0.4, 0.6, false, "en"))
                .thenReturn(List.of("fallback"));

        var response = service.analyze(requestWithoutLyrics);

        assertThat(response.valence()).isEqualTo(0.4);
        assertThat(response.tags()).contains("fallback");
    }
}
