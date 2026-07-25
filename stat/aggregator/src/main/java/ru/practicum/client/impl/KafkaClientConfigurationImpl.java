package ru.practicum.client.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.stereotype.Component;
import ru.practicum.client.ClientConfiguration;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.Properties;

@Slf4j
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class KafkaClientConfigurationImpl implements ClientConfiguration {
    private final KafkaProperties kafkaProperties;
    private Consumer<String, UserActionAvro> consumer;
    private Producer<String, SpecificRecordBase> producer;

    @Override
    public Consumer<String, UserActionAvro> getConsumer() {
        if (consumer == null) {
            initConsumer();
        }
        return consumer;
    }

    @Override
    public Producer<String, SpecificRecordBase> getProducer() {
        if (producer == null) {
            initProducer();
        }
        return producer;
    }

    @Override
    public void stop() {
        if (consumer != null) {
            log.info("Closing Kafka consumer...");
            consumer.close();
        }

        if (producer != null) {
            log.info("Closing Kafka producer...");
            producer.close();
        }
    }

    private void initConsumer() {
        log.info("Initializing Kafka consumer with properties: groupId={}, autoOffsetReset={}, enableAutoCommit={}",
                kafkaProperties.getConsumer().getGroupId(),
                kafkaProperties.getConsumer().getAutoOffsetReset(),
                kafkaProperties.getConsumer().isEnableAutoCommit());

        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getKeyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getValueDeserializer());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                String.valueOf(kafkaProperties.getConsumer().isEnableAutoCommit()));

        consumer = new KafkaConsumer<>(config);
        log.info("Kafka consumer initialized successfully");
    }

    private void initProducer() {
        KafkaProperties.ProducerConfig producerConfig = kafkaProperties.getProducer();

        log.info("Initializing Kafka producer with properties: groupId={}, acks={}, retries={}, " +
                        "maxInFlightRequests={}, deliveryTimeout={}, requestTimeout={}, enableIdempotence={}",
                producerConfig.getGroupId(),
                producerConfig.getAcks(),
                producerConfig.getRetries(),
                producerConfig.getMaxInFlightRequestsPerConnection(),
                producerConfig.getDeliveryTimeoutMs(),
                producerConfig.getRequestTimeoutMs(),
                producerConfig.getEnableIdempotence());

        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producerConfig.getKeySerializer());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producerConfig.getValueSerializer());

        config.put(ProducerConfig.ACKS_CONFIG, producerConfig.getAcks());
        config.put(ProducerConfig.RETRIES_CONFIG, producerConfig.getRetries());
        config.put(ProducerConfig.LINGER_MS_CONFIG, producerConfig.getLingerMs());
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                producerConfig.getMaxInFlightRequestsPerConnection().toString());
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                producerConfig.getDeliveryTimeoutMs().toString());
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                producerConfig.getRequestTimeoutMs().toString());
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                producerConfig.getEnableIdempotence().toString());

        producer = new KafkaProducer<>(config);
        log.info("Kafka producer initialized successfully");
    }
}