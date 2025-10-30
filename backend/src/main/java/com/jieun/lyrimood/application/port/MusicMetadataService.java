package com.jieun.lyrimood.application.port;

import com.jieun.lyrimood.domain.model.MusicMetadata;
import java.util.Optional;

public interface MusicMetadataService {

    Optional<MusicMetadata> lookup(String title, String artist);
}

