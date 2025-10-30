package com.jieun.lyrimood.application.port;

import java.util.List;

public interface TagService {

    List<String> suggestTags(String title, String artist, String lyrics, double valence, double arousal, boolean profane, String languageCode);
}

