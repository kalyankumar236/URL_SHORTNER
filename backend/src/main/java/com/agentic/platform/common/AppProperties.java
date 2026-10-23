package com.agentic.platform.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed application configuration bound from the {@code app.*} namespace.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private ShortCfg shortProps = new ShortCfg();
    private Admin admin = new Admin();
    private RateLimit rateLimit = new RateLimit();
    private Analytics analytics = new Analytics();
    private Cors cors = new Cors();
    private Orchestration orchestration = new Orchestration();
    private Llm llm = new Llm();

    public ShortCfg getShort() { return shortProps; }
    public void setShort(ShortCfg shortProps) { this.shortProps = shortProps; }
    public Admin getAdmin() { return admin; }
    public void setAdmin(Admin admin) { this.admin = admin; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Analytics getAnalytics() { return analytics; }
    public void setAnalytics(Analytics analytics) { this.analytics = analytics; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Orchestration getOrchestration() { return orchestration; }
    public void setOrchestration(Orchestration orchestration) { this.orchestration = orchestration; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public static class ShortCfg {
        private String baseUrl = "http://localhost:8080";
        private int codeLength = 7;
        private int maxUrlLength = 2048;
        private int maxAliasLength = 32;
        private int defaultRedirectStatus = 302;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getCodeLength() { return codeLength; }
        public void setCodeLength(int codeLength) { this.codeLength = codeLength; }
        public int getMaxUrlLength() { return maxUrlLength; }
        public void setMaxUrlLength(int maxUrlLength) { this.maxUrlLength = maxUrlLength; }
        public int getMaxAliasLength() { return maxAliasLength; }
        public void setMaxAliasLength(int maxAliasLength) { this.maxAliasLength = maxAliasLength; }
        public int getDefaultRedirectStatus() { return defaultRedirectStatus; }
        public void setDefaultRedirectStatus(int s) { this.defaultRedirectStatus = s; }
    }

    public static class Admin {
        private String username = "admin";
        private String password = "admin";
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int capacity = 60;
        private int refillPerMinute = 60;
        private boolean failOpen = true;
        private String backend = "memory";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillPerMinute() { return refillPerMinute; }
        public void setRefillPerMinute(int r) { this.refillPerMinute = r; }
        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
    }

    public static class Analytics {
        private boolean enabled = true;
        private String hashSalt = "local-dev-salt";
        private int maxRetries = 3;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHashSalt() { return hashSalt; }
        public void setHashSalt(String hashSalt) { this.hashSalt = hashSalt; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:4200";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Orchestration {
        private int maxParallelTasks = 4;
        private int defaultMaxAttempts = 3;
        private long backoffBaseMillis = 50;
        private long backoffMaxMillis = 2000;
        public int getMaxParallelTasks() { return maxParallelTasks; }
        public void setMaxParallelTasks(int v) { this.maxParallelTasks = v; }
        public int getDefaultMaxAttempts() { return defaultMaxAttempts; }
        public void setDefaultMaxAttempts(int v) { this.defaultMaxAttempts = v; }
        public long getBackoffBaseMillis() { return backoffBaseMillis; }
        public void setBackoffBaseMillis(long v) { this.backoffBaseMillis = v; }
        public long getBackoffMaxMillis() { return backoffMaxMillis; }
        public void setBackoffMaxMillis(long v) { this.backoffMaxMillis = v; }
    }

    public static class Llm {
        private String provider = "mock";
        private String apiKey = "";
        private String baseUrl = "";
        private String model = "mock-model-v1";
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
