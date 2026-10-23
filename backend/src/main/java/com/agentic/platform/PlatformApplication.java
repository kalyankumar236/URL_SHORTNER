package com.agentic.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Agentic SDLC Platform backend.
 *
 * <p>Hosts two cohesive domains in one deployable: the URL Shortener and the
 * deterministic Agentic SDLC Orchestration Engine.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
