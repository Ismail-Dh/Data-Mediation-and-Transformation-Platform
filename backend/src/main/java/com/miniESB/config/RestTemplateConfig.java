package com.miniESB.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Fournit un RestTemplate de base.
 * Le ProviderDispatchServiceImpl crée ses propres instances avec les timeouts
 * par provider en utilisant ce builder comme point de départ.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder();
    }

    /**
     * RestTemplate par défaut (timeout 30 s) utilisé pour les appels sans provider
     * configuré ou pour les tests d'intégration.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}