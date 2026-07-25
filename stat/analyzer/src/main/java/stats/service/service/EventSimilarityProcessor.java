package stats.service.service;

import deserializer.EventSimilarityDeserializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import stats.service.client.impl.KafkaClientConfigurationImpl;
import stats.service.mapper.EventSimilarityMapper;
import stats.service.model.EventSimilarity;
import stats.service.repository.EventSimilarityRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSimilarityProcessor {

    @Value("${kafka.topics.event-similarity:stats.events-similarity.v1}")
    private String topic;

    @Value("${kafka.consumer.group-id-similarity:similarity-analyzer-group}")
    private String groupId;

    private final EventSimilarityRepository eventSimilarityRepository;
    private final KafkaClientConfigurationImpl<EventSimilarityAvro> client;
    private Consumer<String, EventSimilarityAvro> consumer;

    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        this.consumer = client.initConsumer(groupId, EventSimilarityDeserializer.class);
        log.info("EventSimilarityProcessor инициализирован с топиком: {}, groupId: {}", topic, groupId);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    public void start() {
        try {
            consumer.subscribe(List.of(topic));
            log.info("Подписан на топик: {}", topic);

            while (running) {
                ConsumerRecords<String, EventSimilarityAvro> records =
                        consumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                List<EventSimilarityAvro> avroList = new ArrayList<>();
                for (ConsumerRecord<String, EventSimilarityAvro> record : records) {
                    avroList.add(record.value());
                }

                processBatch(avroList);

                consumer.commitSync();
                log.debug("Успешно обработан батч из {} сообщений", avroList.size());
            }
        } catch (WakeupException ignored) {
            log.info("Получен сигнал остановки");
        } catch (Exception e) {
            log.error("Ошибка во время получения данных", e);
        } finally {
            try {
                if (consumer != null) {
                    consumer.commitSync();
                    consumer.close();
                    log.info("Консьюмер закрыт");
                }
            } catch (Exception e) {
                log.error("Ошибка при закрытии консьюмера", e);
            }
        }
    }

    @Transactional
    protected void processBatch(List<EventSimilarityAvro> avroList) {
        if (avroList.isEmpty()) {
            return;
        }

        List<EventSimilarity> newSimilarities = avroList.stream()
                .map(EventSimilarityMapper::toEntity)
                .toList();

        List<Long> eventIds = new ArrayList<>();
        for (EventSimilarity sim : newSimilarities) {
            eventIds.add(sim.getEvent1());
            eventIds.add(sim.getEvent2());
        }

        List<EventSimilarity> existingSimilarities = eventSimilarityRepository
                .findByEvent1InOrEvent2In(eventIds);

        Map<String, EventSimilarity> existingMap = existingSimilarities.stream()
                .collect(Collectors.toMap(
                        sim -> sim.getEvent1() + ":" + sim.getEvent2(),
                        sim -> sim,
                        (existing, replacement) -> existing
                ));

        List<EventSimilarity> toSave = new ArrayList<>();
        List<EventSimilarity> toUpdate = new ArrayList<>();

        for (EventSimilarity newSim : newSimilarities) {
            String key = newSim.getEvent1() + ":" + newSim.getEvent2();
            EventSimilarity existing = existingMap.get(key);

            if (existing != null) {
                existing.setSimilarity(newSim.getSimilarity());
                existing.setTs(newSim.getTs());
                toUpdate.add(existing);
                log.debug("Обновлена запись для event1={}, event2={}, similarity={}",
                        newSim.getEvent1(), newSim.getEvent2(), newSim.getSimilarity());
            } else {
                toSave.add(newSim);
                log.debug("Сохранена новая запись для event1={}, event2={}, similarity={}",
                        newSim.getEvent1(), newSim.getEvent2(), newSim.getSimilarity());
            }
        }

        if (!toSave.isEmpty()) {
            eventSimilarityRepository.saveAll(toSave);
        }
        if (!toUpdate.isEmpty()) {
            eventSimilarityRepository.saveAll(toUpdate);
        }

        log.info("Обработан батч: {} новых, {} обновленных", toSave.size(), toUpdate.size());
    }
}