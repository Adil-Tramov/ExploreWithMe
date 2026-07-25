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
import stats.service.config.KafkaTopicsProperties;
import stats.service.mapper.EventSimilarityMapper;
import stats.service.model.EventSimilarity;
import stats.service.repository.EventSimilarityRepository;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSimilarityProcessor {
    private final EventSimilarityRepository eventSimilarityRepository;
    private final KafkaClientConfigurationImpl<EventSimilarityAvro> client;
    private final KafkaTopicsProperties kafkaTopics;
    private Consumer<String, EventSimilarityAvro> consumer;

    @PostConstruct
    public void init() {
        String groupId = kafkaTopics.getAnalyzerGroupId();
        this.consumer = client.initConsumer(groupId, EventSimilarityDeserializer.class);
    }

    public void start() {
        try {
            String inputTopic = kafkaTopics.getInputTopic();
            consumer.subscribe(Collections.singletonList(inputTopic));

            while (true) {
                ConsumerRecords<String, EventSimilarityAvro> records =
                        consumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                processRecords(records);

                consumer.commitSync();
                log.debug("Коммит offset выполнен успешно");
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время получения данных", e);
        } finally {
            closeConsumer();
        }
    }

    private void processRecords(ConsumerRecords<String, EventSimilarityAvro> records) {
        List<EventSimilarity> newSimilarities = new ArrayList<>();

        for (ConsumerRecord<String, EventSimilarityAvro> record : records) {
            EventSimilarity similarity = EventSimilarityMapper.toEntity(record.value());
            newSimilarities.add(similarity);
        }

        if (newSimilarities.isEmpty()) {
            return;
        }

        Set<Long> eventIdsSet = new HashSet<>();
        for (EventSimilarity sim : newSimilarities) {
            eventIdsSet.add(sim.getEvent1());
            eventIdsSet.add(sim.getEvent2());
        }
        List<Long> eventIds = new ArrayList<>(eventIdsSet);

        List<EventSimilarity> existingSimilarities = eventSimilarityRepository
                .findByEvent1InOrEvent2In(eventIds);

        Map<String, EventSimilarity> existingMap = new HashMap<>();
        for (EventSimilarity sim : existingSimilarities) {
            existingMap.put(createKey(sim.getEvent1(), sim.getEvent2()), sim);
        }

        for (EventSimilarity newSimilarity : newSimilarities) {
            String key = createKey(newSimilarity.getEvent1(), newSimilarity.getEvent2());
            EventSimilarity existing = existingMap.get(key);

            if (existing != null) {
                existing.setSimilarity(newSimilarity.getSimilarity());
                existing.setTs(newSimilarity.getTs());
                eventSimilarityRepository.save(existing);
                log.debug("Обновлена запись для event1={}, event2={}, similarity={}",
                        newSimilarity.getEvent1(),
                        newSimilarity.getEvent2(),
                        newSimilarity.getSimilarity());
            } else {
                eventSimilarityRepository.save(newSimilarity);
                log.debug("Сохранена новая запись для event1={}, event2={}, similarity={}",
                        newSimilarity.getEvent1(),
                        newSimilarity.getEvent2(),
                        newSimilarity.getSimilarity());
            }
        }
    }

    private String createKey(Long event1, Long event2) {
        return event1 < event2
                ? event1 + "_" + event2
                : event2 + "_" + event1;
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                log.info("Закрываем консьюмер");
                consumer.close();
            }
        } catch (Exception e) {
            log.error("Ошибка при закрытии консьюмера", e);
        }
    }
}