package com.example.offer.mediators;

import com.example.offer.offer.CompletenessPolicy;
import com.example.offer.offer.Decisions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
class MediatorConfiguration {

    @Bean
    CompletenessPolicy completenessPolicy(
            @Value("${offer.requirements:TITLE_REQUIRED,DESCRIPTION_REQUIRED,PHOTO_REQUIRED,PRICE_REQUIRED}")
            String requirementCodes) {
        return CompletenessPolicy.of(codes(requirementCodes));
    }

    @Bean
    Decisions decisions() {
        return new Decisions();
    }

    private static List<String> codes(String requirementCodes) {
        return Arrays.stream(requirementCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
