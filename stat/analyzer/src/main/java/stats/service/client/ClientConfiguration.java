package stats.service.client;

import deserializer.BaseAvroDeserializer;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;


public interface ClientConfiguration<T extends SpecificRecordBase> {

    Consumer<String, T> initConsumer(String groupId, Class<? extends BaseAvroDeserializer<T>> deserializer);
}
