package stats.service.client.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka.consumer")
public class KafkaConsumerProperties {
    private String bootstrapServers;
    private String groupId;
    private String autoOffsetReset;
    private String keyDeserializer;
    private String valueDeserializer;
}
