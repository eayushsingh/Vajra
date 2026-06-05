package com.railways.anomaly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================
 * CWR Anomaly Detection Engine — Spring Boot Entry Point
 * Project: Real-Time Predictive Anomaly Engine
 *          for Continuous Welded Rail Stress Management
 * ============================================================
 *
 * Starts an embedded Tomcat server on port 8080.
 * The static HTML dashboard is served at http://localhost:8080/
 * The REST API is available under /api/**
 */
@SpringBootApplication
public class AnomalyEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnomalyEngineApplication.class, args);
    }
}
