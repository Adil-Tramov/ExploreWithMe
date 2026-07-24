package stats.service.service;

import deserializer.UserActionDeserializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import stats.service.client.impl.KafkaClientConfigurationImpl;
import stats.service.config.KafkaTopicsProperties;
import stats.service.mapper.UserActionMapper;
import stats.service.model.UserAction;
import stats.service.repository.UserActionRepository;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor {
    private final UserActionRepository userActionRepository;
    private final KafkaClientConfigurationImpl<UserActionAvro> client;
    private final KafkaTopicsProperties kafkaTopics;
    private Consumer<String, UserActionAvro> consumer;

    @PostConstruct
    public void init() {
        String groupId = kafkaTopics.getAnalyzerGroupId();
        this.consumer = client.initConsumer(groupId, UserActionDeserializer.class);
    }

    public void start() {
        try {
            String inputTopic = kafkaTopics.getInputTopic();
            consumer.subscribe(Collections.singletonList(inputTopic));

            while (true) {
                ConsumerRecords<String, UserActionAvro> records =
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

    private void processRecords(ConsumerRecords<String, UserActionAvro> records) {
        List<UserAction> newActions = new ArrayList<>();

        for (ConsumerRecord<String, UserActionAvro> record : records) {
            UserAction action = UserActionMapper.toEntity(record.value());
            newActions.add(action);
        }

        if (newActions.isEmpty()) {
            return;
        }

        List<Long> userIds = newActions.stream()
                .map(UserAction::getUserId)
                .distinct()
                .toList();

        List<Long> eventIds = newActions.stream()
                .map(UserAction::getEventId)
                .distinct()
                .toList();

        List<UserAction> existingActions = userActionRepository
                .findAllByUserIdInAndEventIdIn(userIds, eventIds);

        Map<String, UserAction> existingMap = new HashMap<>();
        for (UserAction action : existingActions) {
            existingMap.put(createKey(action.getUserId(), action.getEventId()), action);
        }

        for (UserAction newAction : newActions) {
            String key = createKey(newAction.getUserId(), newAction.getEventId());
            UserAction existing = existingMap.get(key);

            if (existing != null) {
                if (newAction.getRating() > existing.getRating()) {
                    existing.setRating(newAction.getRating());
                    existing.setTs(newAction.getTs());
                    userActionRepository.save(existing);
                    log.debug("Обновлена запись для user_id={}, event_id={}, rating={} (было={})",
                            newAction.getUserId(),
                            newAction.getEventId(),
                            newAction.getRating(),
                            existing.getRating());
                } else {
                    log.debug("Пропущено обновление для user_id={}, event_id={}, rating={} (текущий={})",
                            newAction.getUserId(),
                            newAction.getEventId(),
                            newAction.getRating(),
                            existing.getRating());
                }
            } else {
                userActionRepository.save(newAction);
                log.debug("Сохранена новая запись для user_id={}, event_id={}, rating={}",
                        newAction.getUserId(),
                        newAction.getEventId(),
                        newAction.getRating());
            }
        }
    }

    private String createKey(Long userId, Long eventId) {
        return userId + "_" + eventId;
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