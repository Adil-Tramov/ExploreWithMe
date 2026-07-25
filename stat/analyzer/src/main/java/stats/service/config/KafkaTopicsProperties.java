package stats.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicsProperties {
    private String inputTopic = "stats.events-similarity.v1";
    private String outputTopic = "stats.events-similarity.v1";
    private String analyzerGroupId = "similarity-analyzer-group";
}