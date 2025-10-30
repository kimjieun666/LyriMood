package com.jieun.lyrimood.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_logs")
public class AnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String artist;
    @Column(length = 16)
    private String label;
    @Column(length = 8)
    private String lang;
    private Boolean profane;
    private Double valence;
    private Double arousal;
    private Integer lyricsLength;
    private Double languageConfidence;
    @Column(length = 64)
    private String musicBrainzId;
    private LocalDate releaseDate;
    @Column(length = 8)
    private String releaseCountry;
    private OffsetDateTime createdAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String lyrics;

    @ElementCollection
    @CollectionTable(name = "analysis_log_genres", joinColumns = @JoinColumn(name = "analysis_log_id"))
    @Column(name = "genre", length = 64)
    private List<String> genres = new ArrayList<>();

    @Column(length = 16)
    private String acousticKey;

    private Double acousticTempo;

    @Column(length = 32)
    private String acousticMood;

    @ElementCollection
    @CollectionTable(name = "analysis_log_tags", joinColumns = @JoinColumn(name = "analysis_log_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "analysis_log_highlights", joinColumns = @JoinColumn(name = "analysis_log_id"))
    @Column(name = "highlight", columnDefinition = "TEXT")
    private List<String> highlights = new ArrayList<>();

    @Column(length = 128)
    private String lyricsDigest;

    protected AnalysisLog() {
    }

    public AnalysisLog(String title,
                       String artist,
                       String label,
                       String lang,
                       boolean profane,
                       double valence,
                       double arousal,
                       Integer lyricsLength,
                       Double languageConfidence,
                       String musicBrainzId,
                       java.time.LocalDate releaseDate,
                       String releaseCountry,
                       List<String> tags,
                       List<String> genres,
                       String acousticKey,
                       Double acousticTempo,
                       String acousticMood,
                       String lyrics,
                       List<String> highlights,
                       String lyricsDigest,
                       OffsetDateTime createdAt) {
        this.title = title;
        this.artist = artist;
        this.label = label;
        this.lang = lang;
        this.profane = profane;
        this.valence = valence;
        this.arousal = arousal;
        this.lyricsLength = lyricsLength;
        this.languageConfidence = languageConfidence;
        this.musicBrainzId = musicBrainzId;
        this.releaseDate = releaseDate;
        this.releaseCountry = releaseCountry;
        this.lyrics = lyrics;
        this.tags = new ArrayList<>(tags);
        if (genres != null) {
            this.genres = new ArrayList<>(genres);
        }
        this.acousticKey = acousticKey;
        this.acousticTempo = acousticTempo;
        this.acousticMood = acousticMood;
        if (highlights != null) {
            this.highlights = new ArrayList<>(highlights);
        }
        this.lyricsDigest = lyricsDigest;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getLabel() {
        return label;
    }

    public String getLang() {
        return lang;
    }

    public boolean isProfane() {
        return Boolean.TRUE.equals(profane);
    }

    public double getValence() {
        return valence;
    }

    public double getArousal() {
        return arousal;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getLyricsDigest() {
        return lyricsDigest;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getLyricsLength() {
        return lyricsLength;
    }

    public Double getLanguageConfidence() {
        return languageConfidence;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public java.time.LocalDate getReleaseDate() {
        return releaseDate;
    }

    public String getReleaseCountry() {
        return releaseCountry;
    }

    public String getLyrics() {
        return lyrics;
    }

    public List<String> getGenres() {
        return genres;
    }

    public String getAcousticKey() {
        return acousticKey;
    }

    public Double getAcousticTempo() {
        return acousticTempo;
    }

    public String getAcousticMood() {
        return acousticMood;
    }

    public List<String> getHighlights() {
        return highlights;
    }
}
