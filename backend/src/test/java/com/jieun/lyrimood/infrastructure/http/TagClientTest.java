package com.jieun.lyrimood.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TagClientTest {

    @Test
    void localKeyphraseFallbackProvidesThreeToEightTags() {
        TagClient client = new TagClient();

        List<String> tags = client.suggestTags(
                "Midnight City",
                "Neon Dreams",
                "Lights lights lights in the midnight city where dreams ignite and hearts collide",
                0.72,
                0.41,
                false,
                "en");

        assertThat(tags).hasSizeBetween(3, 8);
        assertThat(tags).doesNotContainNull();
    }
}
