package stats.service.service;

import deserializer.EventSimilarityDeserializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import stats.service.client.impl.KafkaClientConfigurationImpl;
import stats.service.mapper.EventSimilarityMapper;
import stats.service.model.EventSimilarity;
import stats.service.repository.EventSimilarityRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSimilarityProcessor {
    private final List<String> TOPICS = List.of("stats.events-similarity.v1");
    private final String GROUPID = "similarity-analyzer-group";

    private final EventSimilarityRepository eventSimilarityRepository;
    private final KafkaClientConfigurationImpl<EventSimilarityAvro> client;
    private Consumer<String, EventSimilarityAvro> consumer;

    @PostConstruct
    public void init() {
        this.consumer = client.initConsumer(GROUPID, EventSimilarityDeserializer.class);
    }

    public void start() {
        try {
            consumer.subscribe(TOPICS);
            while (true) {
                ConsumerRecords<String, EventSimilarityAvro> records =
                        consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, EventSimilarityAvro> record : records) {
                    EventSimilarityAvro avro = record.value();
                    EventSimilarity eventSimilarity = EventSimilarityMapper.toEntity(avro);

                    Optional<EventSimilarity> existing = eventSimilarityRepository
                            .findByEvent1AndEvent2(eventSimilarity.getEvent1(), eventSimilarity.getEvent2());

                    if (existing.isPresent()) {
                        EventSimilarity existingSimilarity = existing.get();
                        existingSimilarity.setSimilarity(eventSimilarity.getSimilarity());
                        existingSimilarity.setTs(eventSimilarity.getTs());
                        eventSimilarityRepository.save(existingSimilarity);
                        log.debug("Обновлена запись для event1={}, event2={}, similarity={}",
                                eventSimilarity.getEvent1(),
                                eventSimilarity.getEvent2(),
                                eventSimilarity.getSimilarity());
                    } else {
                        eventSimilarityRepository.save(eventSimilarity);
                        log.debug("Сохранена новая запись для event1={}, event2={}, similarity={}",
                                eventSimilarity.getEvent1(),
                                eventSimilarity.getEvent2(),
                                eventSimilarity.getSimilarity());
                    }
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время получения данных", e);
        } finally {
            try {
                consumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер");
                consumer.close();
            }
        }
    }
}
