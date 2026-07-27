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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final ClientConfiguration client;
    private final KafkaTopicsProperties topicsProperties;
    private final Map<Integer, Map<Integer, Double>> eventUserActionMatrix = new HashMap<>();
    private final Map<Integer, Double> eventSumValue = new HashMap<>();
    private final Map<Integer, Map<Integer, Double>> minWeightsSums = new HashMap<>();

    public void start() {
        try {
            client.getConsumer().subscribe(topicsProperties.getConsumerTopics());

            while (true) {
                ConsumerRecords<String, UserActionAvro> records =
                        client.getConsumer().poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    processUserAction(record.value());
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий", e);
        } finally {
            closeResources();
        }
    }

    private void processUserAction(UserActionAvro data) {
        int eventId = data.getEventId();
        int userId = data.getUserId();

        double oldWeight = getUserWeight(eventId, userId);
        double newWeight = computeWeightActionType(data.getActionType());

        if (newWeight <= oldWeight) {
            log.debug("Вес не изменился: event={}, user={}, old={}, new={}",
                    eventId, userId, oldWeight, newWeight);
            return;
        }

        log.info("Обновление: event={}, user={}, weight: {} -> {}", eventId, userId, oldWeight, newWeight);

        double oldEventSum = eventSumValue.getOrDefault(eventId, 0.0);
        double deltaWeight = newWeight - oldWeight;

        updateUserWeight(eventId, userId, newWeight);
        eventSumValue.put(eventId, oldEventSum + deltaWeight);

        recalculateSimilarities(eventId, userId, oldWeight, newWeight, oldEventSum);
    }

    private double getUserWeight(int eventId, int userId) {
        Map<Integer, Double> userWeights = eventUserActionMatrix.get(eventId);
        return userWeights != null ? userWeights.getOrDefault(userId, 0.0) : 0.0;
    }

    private void updateUserWeight(int eventId, int userId, double newWeight) {
        eventUserActionMatrix
                .computeIfAbsent(eventId, k -> new HashMap<>())
                .put(userId, newWeight);
    }

    private void recalculateSimilarities(int eventId, int userId, double oldWeight,
                                         double newWeight, double oldEventSum) {
        double deltaWeight = newWeight - oldWeight;
        double newEventSum = oldEventSum + deltaWeight;

        for (int otherEventId : eventSumValue.keySet()) {
            if (otherEventId == eventId) {
                continue;
            }

            double otherUserWeight = getUserWeight(otherEventId, userId);

            int firstKey = Math.min(eventId, otherEventId);
            int secondKey = Math.max(eventId, otherEventId);

            double otherEventSum = eventSumValue.get(otherEventId);

            double sumFirst = (firstKey == eventId) ? newEventSum : otherEventSum;
            double sumSecond = (secondKey == eventId) ? newEventSum : otherEventSum;

            if (sumFirst <= 0 || sumSecond <= 0) {
                continue;
            }

            double newMin = Math.min(newWeight, otherUserWeight);
            double oldMin = Math.min(oldWeight, otherUserWeight);
            double deltaMin = newMin - oldMin;

            double oldMinSum = getMinSum(firstKey, secondKey);
            double newMinSum = oldMinSum + deltaMin;

            minWeightsSums
                    .computeIfAbsent(firstKey, k -> new HashMap<>())
                    .put(secondKey, newMinSum);

            if (otherUserWeight > 0) {
                double oldSumFirst = (firstKey == eventId) ? oldEventSum : otherEventSum;
                double oldSumSecond = (secondKey == eventId) ? oldEventSum : otherEventSum;

                double oldSimilarity = 0.0;
                if (oldSumFirst > 0 && oldSumSecond > 0) {
                    oldSimilarity = oldMinSum / (Math.sqrt(oldSumFirst) * Math.sqrt(oldSumSecond));
                }

                double newSimilarity = newMinSum / (Math.sqrt(sumFirst) * Math.sqrt(sumSecond));

                if (Math.abs(newSimilarity - oldSimilarity) > 0.0001) {
                    log.info("Схожесть пары ({}, {}) изменилась: {} -> {}",
                            firstKey, secondKey, oldSimilarity, newSimilarity);
                    sendSimilarityEvent(firstKey, secondKey, newMinSum, sumFirst, sumSecond);
                }
            }
        }
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

        client.getProducer().send(new ProducerRecord<>(topicsProperties.getProducerTopic(), avro));
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