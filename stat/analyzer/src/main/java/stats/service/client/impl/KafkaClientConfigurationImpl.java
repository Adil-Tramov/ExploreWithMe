package stats.service.client.impl;

import deserializer.BaseAvroDeserializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import stats.service.client.ClientConfiguration;

import java.util.Properties;

@Getter
@Setter
@Component
@RequiredArgsConstructor
public class KafkaClientConfigurationImpl<T extends SpecificRecordBase> implements ClientConfiguration<T>, AutoCloseable {
    private final KafkaConsumerProperties kafkaConsumerProperties;
    private Consumer<String, T> consumer;

    public Consumer<String, T> initConsumer(String groupId, Class<? extends BaseAvroDeserializer<T>> deserializer) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConsumerProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaConsumerProperties.getKeyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        return new KafkaConsumer<>(config);
    }

    @Override
    public void close() {
        if (consumer != null) {
            consumer.close();
        }
    }
}
