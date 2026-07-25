package ru.practicum.client.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    private String bootstrapServers;

    private ConsumerConfig consumer = new ConsumerConfig();

    private ProducerConfig producer = new ProducerConfig();

    @Data
    public static class ConsumerConfig {
        private String groupId;
        private String autoOffsetReset;
        private String keyDeserializer;
        private String valueDeserializer;
        private Boolean enableAutoCommit = false;
    }

    @Data
    public static class ProducerConfig {
        private String groupId;
        private String keySerializer;
        private String valueSerializer;
        private String acks = "all";
        private Integer retries = 3;
        private Integer lingerMs = 100;
        
        private Integer maxInFlightRequestsPerConnection = 1;
        private Integer deliveryTimeoutMs = 120000;
        private Integer requestTimeoutMs = 30000;
        private Boolean enableIdempotence = true;
    }
}