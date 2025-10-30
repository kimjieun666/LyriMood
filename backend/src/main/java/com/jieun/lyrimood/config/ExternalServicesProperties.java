package com.jieun.lyrimood.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external")
public class ExternalServicesProperties {

    private final ServiceProperties language = new ServiceProperties();
    private final ServiceProperties musicbrainz = new ServiceProperties();
    private final ServiceProperties detectlanguage = new ServiceProperties();
    private final ServiceProperties acousticbrainz = new ServiceProperties();
    private final ServiceProperties gemini = new ServiceProperties();

    public ServiceProperties getLanguage() {
        return language;
    }

    public ServiceProperties getMusicbrainz() {
        return musicbrainz;
    }

    public ServiceProperties getDetectlanguage() {
        return detectlanguage;
    }

    public ServiceProperties getAcousticbrainz() {
        return acousticbrainz;
    }

    public ServiceProperties getGemini() {
        return gemini;
    }

    public static class ServiceProperties {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(2);
        private int maxAttempts = 3;
        private Duration backoff = Duration.ofMillis(200);
        private int rateLimitForPeriod = 10;
        private Duration rateLimitRefreshPeriod = Duration.ofSeconds(1);
        private String apiKeyHeader;
        private String apiKeyValue;
        private String model;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }

        public int getRateLimitForPeriod() {
            return rateLimitForPeriod;
        }

        public void setRateLimitForPeriod(int rateLimitForPeriod) {
            this.rateLimitForPeriod = rateLimitForPeriod;
        }

        public Duration getRateLimitRefreshPeriod() {
            return rateLimitRefreshPeriod;
        }

        public void setRateLimitRefreshPeriod(Duration rateLimitRefreshPeriod) {
            this.rateLimitRefreshPeriod = rateLimitRefreshPeriod;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }

        public String getApiKeyValue() {
            return apiKeyValue;
        }

        public void setApiKeyValue(String apiKeyValue) {
            this.apiKeyValue = apiKeyValue;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

}
