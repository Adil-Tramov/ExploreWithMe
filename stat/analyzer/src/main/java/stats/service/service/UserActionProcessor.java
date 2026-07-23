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
import stats.service.mapper.UserActionMapper;
import stats.service.model.UserAction;
import stats.service.repository.UserActionRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor {
    private final List<String> TOPICS = List.of("stats.user-actions.v1");
    private final String GROUPID = "action-analyzer-group";

    private final UserActionRepository userActionRepository;
    private final KafkaClientConfigurationImpl<UserActionAvro> client;
    private Consumer<String, UserActionAvro> consumer;


    @PostConstruct
    public void init() {
        this.consumer = client.initConsumer(GROUPID, UserActionDeserializer.class);
    }

    public void start() {
        try {
            consumer.subscribe(TOPICS);
            while (true) {
                ConsumerRecords<String, UserActionAvro> records =
                        consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    UserActionAvro avro = record.value();
                    UserAction userAction = UserActionMapper.toEntity(avro);

                    Optional<UserAction> existing = userActionRepository
                            .findByUserIdAndEventId(userAction.getUserId(), userAction.getEventId());

                    if (existing.isPresent()) {
                        UserAction existingAction = existing.get();
                        // Оставляем максимальный рейтинг
                        if (userAction.getRating() > existingAction.getRating()) {
                            existingAction.setRating(userAction.getRating());
                            existingAction.setTs(userAction.getTs());
                            userActionRepository.save(existingAction);
                            log.debug("Обновлена запись для user_id={}, event_id={}, rating={} (было={})",
                                    userAction.getUserId(),
                                    userAction.getEventId(),
                                    userAction.getRating(),
                                    existingAction.getRating());
                        } else {
                            log.debug("Пропущено обновление для user_id={}, event_id={}, rating={} (текущий={})",
                                    userAction.getUserId(),
                                    userAction.getEventId(),
                                    userAction.getRating(),
                                    existingAction.getRating());
                        }
                    } else {
                        userActionRepository.save(userAction);
                        log.debug("Сохранена новая запись для user_id={}, event_id={}, rating={}",
                                userAction.getUserId(),
                                userAction.getEventId(),
                                userAction.getRating());
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
