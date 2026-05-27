package com.halilov.online;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point. {@link EnableScheduling} switches on the
 * scheduled tasks elsewhere in the app (email outbox retry sweep,
 * anomaly monitor, daily audit-log snapshot).
 */
@SpringBootApplication
@EnableScheduling
public class MarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }
}
