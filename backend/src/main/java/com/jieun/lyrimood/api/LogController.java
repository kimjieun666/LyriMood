package com.jieun.lyrimood.api;

import com.jieun.lyrimood.api.dto.AnalysisLogResponse;
import com.jieun.lyrimood.application.port.AnalysisLogRepository;
import com.jieun.lyrimood.domain.model.AnalysisLog;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogController {

    private final AnalysisLogRepository repository;

    public LogController(AnalysisLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/logs")
    public String viewLogs(@RequestParam(value = "query", required = false) String query,
                           Model model) {
        List<AnalysisLog> entities = repository.findTop20ByOrderByCreatedAtDesc();
        if (query != null && !query.isBlank()) {
            String lowered = query.toLowerCase();
            entities = entities.stream()
                    .filter(log -> containsIgnoreCase(log.getTitle(), lowered)
                            || containsIgnoreCase(log.getArtist(), lowered)
                            || containsIgnoreCase(log.getLabel(), lowered)
                            || log.getTags().stream().anyMatch(tag -> containsIgnoreCase(tag, lowered))
                            || log.getGenres().stream().anyMatch(genre -> containsIgnoreCase(genre, lowered)))
                    .toList();
            model.addAttribute("query", query);
        }

        List<AnalysisLogResponse> logs = entities.stream()
                .map(LogController::toResponse)
                .toList();
        model.addAttribute("logs", logs);
        return "logs";
    }

    private static boolean containsIgnoreCase(String value, String lowered) {
        return value != null && value.toLowerCase().contains(lowered);
    }

    private static AnalysisLogResponse toResponse(AnalysisLog log) {
        return new AnalysisLogResponse(
                log.getId(),
                log.getTitle(),
                log.getArtist(),
                log.getLabel(),
                log.getLang(),
                log.isProfane(),
                log.getValence(),
                log.getArousal(),
                log.getLyricsLength(),
                log.getLanguageConfidence(),
                log.getMusicBrainzId(),
                log.getReleaseDate(),
                log.getReleaseCountry(),
                copyOfNullable(log.getTags()),
                copyOfNullable(log.getGenres()),
                log.getLyrics(),
                log.getOriginalLyrics(),
                copyOfNullable(log.getHighlights()),
                log.getAcousticKey(),
                log.getAcousticTempo(),
                log.getAcousticMood(),
                log.getLyricsDigest(),
                log.getCreatedAt()
        );
    }

    private static List<String> copyOfNullable(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.copyOf(source);
    }
}
