package com.jieun.lyrimood.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.jieun.lyrimood.shared.ExternalServiceException;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

@Component
public class ResilientExecutor {

    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final ExecutorService executorService;
    private final Map<String, Duration> timeouts;

    public ResilientExecutor(RetryRegistry retryRegistry,
                             RateLimiterRegistry rateLimiterRegistry,
                             ExecutorService executorService,
                             ExternalServicesProperties properties) {
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.executorService = executorService;
        this.timeouts = Map.of(
                "language", properties.getLanguage().getTimeout(),
                "musicbrainz", properties.getMusicbrainz().getTimeout(),
                "detectlanguage", properties.getDetectlanguage().getTimeout(),
                "gemini", properties.getGemini().getTimeout(),
                "acousticbrainz", properties.getAcousticbrainz().getTimeout()
        );
    }

    public <T> T execute(String name, Supplier<T> supplier) {
        Retry retry = retryRegistry.retry(name);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(name);
        Duration timeout = timeouts.getOrDefault(name, Duration.ofSeconds(3));

        Supplier<T> rateLimitedSupplier = io.github.resilience4j.ratelimiter.RateLimiter
                .decorateSupplier(rateLimiter, supplier);
        Supplier<T> retriableSupplier = io.github.resilience4j.retry.Retry
                .decorateSupplier(retry, rateLimitedSupplier);

        CompletableFuture<T> future = CompletableFuture.supplyAsync(retriableSupplier, executorService);
        try {
            return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof TimeoutException) {
                throw new ExternalServiceException(name + " service call timed out", cause);
            }
            throw new ExternalServiceException(name + " service call failed", cause != null ? cause : ex);
        } catch (Exception ex) {
            throw new ExternalServiceException(name + " service call failed", ex);
        }
    }
}
