package ru.practicum.client;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import ru.practicum.ewm.stats.avro.UserActionAvro;

public interface ClientConfiguration {

    Producer<String, SpecificRecordBase> getProducer();

    Consumer<String, UserActionAvro> getConsumer();

    void stop();
}
