package com.jieun.lyrimood.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

@Configuration
public class ResilienceConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService externalServiceExecutor() {
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        return Executors.newFixedThreadPool(threads);
    }

    @Bean
    public RetryRegistry retryRegistry(ExternalServicesProperties properties) {
        RetryRegistry registry = RetryRegistry.ofDefaults();
        Map<String, ExternalServicesProperties.ServiceProperties> map = Map.of(
                "language", properties.getLanguage(),
                "musicbrainz", properties.getMusicbrainz(),
                "detectlanguage", properties.getDetectlanguage(),
                "gemini", properties.getGemini(),
                "acousticbrainz", properties.getAcousticbrainz()
        );
        map.forEach((name, serviceProps) -> {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(serviceProps.getMaxAttempts())
                    .waitDuration(serviceProps.getBackoff())
                    .retryExceptions(RuntimeException.class)
                    .build();
            registry.retry(name, config);
        });
        return registry;
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(ExternalServicesProperties properties) {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        Map<String, ExternalServicesProperties.ServiceProperties> map = Map.of(
                "language", properties.getLanguage(),
                "musicbrainz", properties.getMusicbrainz(),
                "detectlanguage", properties.getDetectlanguage(),
                "gemini", properties.getGemini(),
                "acousticbrainz", properties.getAcousticbrainz()
        );
        map.forEach((name, serviceProps) -> {
            Duration refresh = serviceProps.getRateLimitRefreshPeriod();
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitRefreshPeriod(refresh)
                    .limitForPeriod(serviceProps.getRateLimitForPeriod())
                    .timeoutDuration(serviceProps.getTimeout().isZero() ? Duration.ofMillis(100) : serviceProps.getTimeout())
                    .build();
            registry.rateLimiter(name, config);
        });
        return registry;
    }
}
