package com.example.offer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableRetry
@EnableJpaRepositories(considerNestedRepositories = true)
class AppConfiguration {

    /** The single business zone: the source of both the business date and {@code Audit.at} (RULE-69). */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Warsaw");

    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
