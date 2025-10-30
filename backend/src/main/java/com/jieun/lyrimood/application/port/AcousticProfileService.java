package com.jieun.lyrimood.application.port;

import com.jieun.lyrimood.domain.model.AcousticProfile;
import java.util.Optional;

public interface AcousticProfileService {

    Optional<AcousticProfile> fetchProfile(String musicBrainzId);
}
