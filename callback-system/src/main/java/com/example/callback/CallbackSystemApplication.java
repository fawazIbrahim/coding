package com.example.callback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Callback System.
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Receives callback requests (type + data + target) via REST API</li>
 *   <li>Persists executions to PostgreSQL and dispatches via SKIP LOCKED polling</li>
 *   <li>Executes HTTP callbacks with per-target auth, headers, and timeouts</li>
 *   <li>Tracks every attempt with full audit trail</li>
 *   <li>Retries failures with configurable exponential backoff</li>
 *   <li>Recovers stuck executions automatically — no external broker needed</li>
 * </ul>
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true}
 * in {@code application.yaml}, making every Tomcat worker, scheduler, and HTTP
 * call a lightweight JDK virtual thread (Java 25+).
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class CallbackSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallbackSystemApplication.class, args);
    }
}
