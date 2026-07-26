package ru.practicum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka.producer")
public class KafkaProducerProperties {
    private String groupId;
    private String clientId;
    private String bootstrapServers;
    private String keySerializer;
    private String valueSerializer;
    private String acks = "all";
    private Integer retries = 3;
    private Integer lingerMs = 100;
}