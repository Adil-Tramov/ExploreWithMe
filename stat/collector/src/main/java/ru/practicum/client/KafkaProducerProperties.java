package ru.practicum.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka.producer")
public class KafkaProducerProperties {
    private String bootstrapServers = "localhost:9092";
    private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
    private String valueSerializer = "serializer.AvroSerializer";
    private String acks = "all";
    private Integer retries = 3;
    private Integer lingerMs = 100;
}
