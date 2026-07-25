package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.client.ClientConfiguration;
import ru.practicum.config.KafkaTopicsProperties;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final ClientConfiguration client;
    private final KafkaTopicsProperties kafkaTopics;
    private final Map<Integer, Map<Integer, Double>> eventUserActionMatrix = new HashMap<>();
    private final Map<Integer, Double> eventSumValue = new HashMap<>();
    private final Map<Integer, Map<Integer, Double>> minWeightsSums = new HashMap<>();

    public void start() {
        try {
            String inputTopic = kafkaTopics.getInputTopic();
            client.getConsumer().subscribe(List.of(inputTopic));
            log.info("Подписались на топик: {}", inputTopic);

            while (true) {
                ConsumerRecords<String, UserActionAvro> records =
                        client.getConsumer().poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    processUserAction(record.value());
                }
            }
        } catch (WakeupException ignored) {
            log.info("Получен сигнал WakeupException, завершаем работу...");
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий от датчиков", e);
        } finally {
            closeResources();
        }
    }

    private void processUserAction(UserActionAvro data) {
        log.info("------------------------------");
        log.info("Получены данные: {}", data);

        int eventId = data.getEventId();
        int userId = data.getUserId();

        double oldWeight = getUserWeight(eventId, userId);
        double newWeight = computeWeightActionType(data.getActionType());

        if (newWeight <= oldWeight) {
            log.info("Новый вес {} не превышает старый {}, пересчет не требуется", newWeight, oldWeight);
            return;
        }

        updateUserWeight(eventId, userId, newWeight);
        updateEventSum(eventId, oldWeight, newWeight);
        recalculateSimilarities(eventId, userId, oldWeight, newWeight);
    }

    private double getUserWeight(int eventId, int userId) {
        Map<Integer, Double> userWeights = eventUserActionMatrix.get(eventId);
        return userWeights != null ? userWeights.getOrDefault(userId, 0.0) : 0.0;
    }

    private void updateUserWeight(int eventId, int userId, double newWeight) {
        eventUserActionMatrix
                .computeIfAbsent(eventId, k -> new HashMap<>())
                .put(userId, newWeight);
        log.info("Обновлена матрица действий пользователя для события {}: пользователь {} -> вес {}",
                eventId, userId, newWeight);
    }

    private void updateEventSum(int eventId, double oldWeight, double newWeight) {
        double deltaEvent = newWeight - oldWeight;
        double currentEventSum = eventSumValue.getOrDefault(eventId, 0.0);
        double newEventSum = currentEventSum + deltaEvent;
        eventSumValue.put(eventId, newEventSum);
        log.info("Обновлена сумма весов для события {}: {} -> {}",
                eventId, currentEventSum, newEventSum);
    }

    private void recalculateSimilarities(int eventId, int userId, double oldWeight, double newWeight) {
        for (int otherEventId : eventSumValue.keySet()) {
            if (otherEventId == eventId) {
                continue;
            }

            double otherUserWeight = getUserWeight(otherEventId, userId);

            int firstKey = Math.min(eventId, otherEventId);
            int secondKey = Math.max(eventId, otherEventId);

            double sumFirst = getEventSum(firstKey);
            double sumSecond = getEventSum(secondKey);

            if (sumFirst <= 0 || sumSecond <= 0) {
                continue;
            }

            double oldMinValue = Math.min(oldWeight, otherUserWeight);
            double newMinValue = Math.min(newWeight, otherUserWeight);

            double currentMinSum = getMinSum(firstKey, secondKey);
            double updatedMinSum = currentMinSum + (newMinValue - oldMinValue);

            minWeightsSums
                    .computeIfAbsent(firstKey, k -> new HashMap<>())
                    .put(secondKey, updatedMinSum);

            log.info("Обновлена S_min для пары ({}, {}): {} (oldMin={}, newMin={}, delta={})",
                    firstKey, secondKey, updatedMinSum, oldMinValue, newMinValue, newMinValue - oldMinValue);

            sendSimilarityEvent(firstKey, secondKey, updatedMinSum, sumFirst, sumSecond);
        }
    }

    private double getEventSum(int eventId) {
        return eventSumValue.getOrDefault(eventId, 0.0);
    }

    private double getMinSum(int firstKey, int secondKey) {
        Map<Integer, Double> innerMap = minWeightsSums.get(firstKey);
        return innerMap != null ? innerMap.getOrDefault(secondKey, 0.0) : 0.0;
    }

    private void sendSimilarityEvent(int firstKey, int secondKey, double minSum,
                                     double sumFirst, double sumSecond) {
        double similarity = minSum / (Math.sqrt(sumFirst) * Math.sqrt(sumSecond));

        EventSimilarityAvro avro = EventSimilarityAvro.newBuilder()
                .setEventA(firstKey)
                .setEventB(secondKey)
                .setScore(similarity)
                .setTimestamp(Instant.now())
                .build();

        String outputTopic = kafkaTopics.getOutputTopic();
        client.getProducer().send(new ProducerRecord<>(outputTopic, avro));
        log.info("Отправлено сходство для пары ({}, {}): {}", firstKey, secondKey, similarity);
    }

    private double computeWeightActionType(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private void closeResources() {
        try {
            client.getProducer().flush();
            client.getConsumer().commitSync();
        } finally {
            log.info("Закрываем консьюмер и продюсер");
            client.stop();
        }
    }
}