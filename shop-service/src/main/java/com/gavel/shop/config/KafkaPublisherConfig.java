package com.gavel.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.kafka.KafkaPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaPublisherConfig {

    @Bean
    public KafkaPublisher kafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                         ObjectMapper objectMapper) {
        return new KafkaPublisher(kafkaTemplate, objectMapper);
    }
}
