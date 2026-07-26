package ru.practicum.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.stereotype.Component;
import ru.practicum.config.KafkaProducerProperties;

import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProducerProperties properties;

    public Producer<String, SpecificRecordBase> createProducer() {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, properties.getKeySerializer());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, properties.getValueSerializer());

        config.put(ProducerConfig.ACKS_CONFIG, properties.getAcks());
        config.put(ProducerConfig.RETRIES_CONFIG, properties.getRetries());
        config.put(ProducerConfig.LINGER_MS_CONFIG, properties.getLingerMs());

        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        log.info("KafkaProducer создан с bootstrap.servers={}", properties.getBootstrapServers());
        return new KafkaProducer<>(config);
    }
}