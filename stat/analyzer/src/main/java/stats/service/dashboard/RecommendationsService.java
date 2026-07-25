package stats.service.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import stats.service.model.EventSimilarity;
import stats.service.model.UserAction;
import stats.service.repository.EventSimilarityRepository;
import stats.service.repository.UserActionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationsService {
    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;

    @Transactional(readOnly = true)
    public List<RecommendedEventProto> getRecommendationsForUser(Long userId, int maxResults) {
        log.info("Получение рекомендаций для пользователя ID: {}", userId);

        List<UserAction> userActions = userActionRepository.findAllByUserIdOrderByTsDesc(userId);

        List<Long> recentInteractedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .limit(maxResults)
                .toList();

        if (recentInteractedEvents.isEmpty()) {
            log.info("У пользователя ID: {} нет взаимодействий с событиями", userId);
            return Collections.emptyList();
        }

        List<EventSimilarity> similarEvents = eventSimilarityRepository
                .findByEvent1InOrEvent2InOrderBySimilarityDesc(recentInteractedEvents);

        Map<Long, Float> candidateEvents = new LinkedHashMap<>();

        for (EventSimilarity event : similarEvents) {
            Long event1 = event.getEvent1();
            Long event2 = event.getEvent2();
            Float score = event.getSimilarity();

            boolean hasEvent1 = recentInteractedEvents.contains(event1);
            boolean hasEvent2 = recentInteractedEvents.contains(event2);

            if (hasEvent1 != hasEvent2) {
                Long candidateEventId = hasEvent1 ? event2 : event1;
                if (!recentInteractedEvents.contains(candidateEventId)) {
                    candidateEvents.putIfAbsent(candidateEventId, score);
                }
            }
        }

        if (candidateEvents.isEmpty()) {
            log.info("Нет кандидатов для рекомендаций пользователю ID: {}", userId);
            return Collections.emptyList();
        }

        List<Long> candidateIds = new ArrayList<>(candidateEvents.keySet());
        List<EventSimilarity> allSimilarities = new ArrayList<>();

        for (Long candidateId : candidateIds) {
            allSimilarities.addAll(
                    eventSimilarityRepository.findByEvent1OrEvent2OrderBySimilarityDesc(candidateId)
            );
        }

        Map<Long, List<EventSimilarity>> similaritiesByCandidate = new HashMap<>();
        for (EventSimilarity sim : allSimilarities) {
            Long candidateId = null;
            if (candidateIds.contains(sim.getEvent1())) {
                candidateId = sim.getEvent1();
            } else if (candidateIds.contains(sim.getEvent2())) {
                candidateId = sim.getEvent2();
            }

            if (candidateId != null) {
                similaritiesByCandidate.computeIfAbsent(candidateId, k -> new ArrayList<>()).add(sim);
            }
        }

        Map<Long, Double> userRatings = userActions.stream()
                .collect(Collectors.toMap(
                        UserAction::getEventId,
                        ua -> (double) ua.getRating(),
                        (existing, replacement) -> existing
                ));

        List<RecommendedEventProto> result = new ArrayList<>();

        for (Map.Entry<Long, Float> entry : candidateEvents.entrySet()) {
            Long candidateEventId = entry.getKey();

            List<EventSimilarity> nearEvents = similaritiesByCandidate.getOrDefault(candidateEventId, Collections.emptyList())
                    .stream()
                    .filter(sim -> {
                        Long neighborId = sim.getEvent1().equals(candidateEventId)
                                ? sim.getEvent2()
                                : sim.getEvent1();
                        return recentInteractedEvents.contains(neighborId);
                    })
                    .sorted((s1, s2) -> Float.compare(s2.getSimilarity(), s1.getSimilarity()))
                    .limit(20)
                    .toList();

            if (nearEvents.isEmpty()) {
                continue;
            }

            double weightedSum = 0.0;
            double similaritySum = 0.0;

            for (EventSimilarity neighbor : nearEvents) {
                Long neighborId = neighbor.getEvent1().equals(candidateEventId)
                        ? neighbor.getEvent2()
                        : neighbor.getEvent1();

                double rating = userRatings.getOrDefault(neighborId, 0.0);
                weightedSum += neighbor.getSimilarity() * rating;
                similaritySum += neighbor.getSimilarity();
            }

            double predictedScore = similaritySum > 0 ? weightedSum / similaritySum : 0.0;

            result.add(RecommendedEventProto.newBuilder()
                    .setEventId(Math.toIntExact(candidateEventId))
                    .setScore(predictedScore)
                    .build());
        }

        return result.stream()
                .sorted((p1, p2) -> Double.compare(p2.getScore(), p1.getScore()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecommendedEventProto> getSimilarEvents(Long userId, Long eventId, int maxResults) {
        log.info("Поиск похожих событий: userId={}, eventId={}", userId, eventId);

        List<UserAction> userActions = userActionRepository.findAllByUserId(userId);
        Set<Long> interactedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());

        List<EventSimilarity> similarEvents = eventSimilarityRepository
                .findByEvent1OrEvent2OrderBySimilarityDesc(eventId);

        List<RecommendedEventProto> result = new ArrayList<>();
        for (EventSimilarity event : similarEvents) {
            Long similarEventId = event.getEvent1().equals(eventId)
                    ? event.getEvent2()
                    : event.getEvent1();

            if (!interactedEvents.contains(similarEventId)) {
                result.add(RecommendedEventProto.newBuilder()
                        .setEventId(Math.toIntExact(similarEventId))
                        .setScore(event.getSimilarity())
                        .build());

                if (result.size() >= maxResults) {
                    break;
                }
            }
        }

        log.info("Найдено {} похожих событий для пользователя ID: {}", result.size(), userId);
        return result;
    }

    @Transactional(readOnly = true)
    public List<RecommendedEventProto> getInteractionsCount(List<Integer> eventIdList) {
        log.info("Получение количества взаимодействий для {} событий", eventIdList.size());

        if (eventIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = eventIdList.stream()
                .map(Integer::longValue)
                .toList();

        List<UserAction> usersActions = userActionRepository.findAllByEventIdIn(eventIds);

        Map<Long, Float> eventScore = new HashMap<>();
        for (Integer eventId : eventIdList) {
            eventScore.put(eventId.longValue(), 0.0f);
        }

        usersActions.stream()
                .filter(action -> action.getRating() != null)
                .forEach(action -> {
                    Long eventId = action.getEventId();
                    Float rating = action.getRating();
                    eventScore.merge(eventId, rating, Float::sum);
                });

        List<RecommendedEventProto> result = new ArrayList<>();
        eventScore.forEach((eventId, score) -> {
            result.add(RecommendedEventProto.newBuilder()
                    .setEventId(eventId.intValue())
                    .setScore(score)
                    .build());
        });

        log.info("Рассчитаны оценки для {} событий", result.size());
        return result;
    }
}