package ru.practicum.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka.producer")
public class KafkaProducerProperties {
    private String bootstrapServers;
    private String keySerializer;
    private String valueSerializer;
    private String acks;
    private Integer retries;
    private Integer lingerMs;
    private Integer maxInFlightRequestsPerConnection;
    private Integer deliveryTimeoutMs;
    private Integer requestTimeoutMs;
    private Boolean enableIdempotence;
}