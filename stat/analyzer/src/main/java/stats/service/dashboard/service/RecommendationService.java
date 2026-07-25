package stats.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import stats.service.dashboard.RecommendedEventProto;
import stats.service.model.EventSimilarity;
import stats.service.model.UserAction;
import stats.service.repository.EventSimilarityRepository;
import stats.service.repository.UserActionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;
    
    public List<RecommendedEventProto> getRecommendationsForUser(Long userId, int maxResults) {
        log.debug("Получение рекомендаций для пользователя ID: {}, maxResults: {}", userId, maxResults);

        List<UserAction> userActions = userActionRepository
                .findAllByUserIdOrderByTsDesc(userId);

        if (userActions.isEmpty()) {
            log.info("У пользователя ID: {} нет взаимодействий с событиями", userId);
            return Collections.emptyList();
        }

        List<Long> recentInteractedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .limit(maxResults)
                .collect(Collectors.toList());

        List<EventSimilarity> similarEvents = eventSimilarityRepository
                .findByEvent1InOrEvent2InOrderBySimilarityDesc(recentInteractedEvents);

        if (similarEvents.isEmpty()) {
            log.info("Нет похожих событий для пользователя {}", userId);
            return Collections.emptyList();
        }

        Map<Long, Float> candidateScores = new HashMap<>();
        Set<Long> interactedSet = new HashSet<>(recentInteractedEvents);

        for (EventSimilarity sim : similarEvents) {
            Long event1 = sim.getEvent1();
            Long event2 = sim.getEvent2();

            boolean hasEvent1 = interactedSet.contains(event1);
            boolean hasEvent2 = interactedSet.contains(event2);

            if (hasEvent1 != hasEvent2) {
                Long candidateId = hasEvent1 ? event2 : event1;
                if (!interactedSet.contains(candidateId)) {
                    candidateScores.putIfAbsent(candidateId, sim.getSimilarity());
                }
            }
        }

        if (candidateScores.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> candidateIds = new ArrayList<>(candidateScores.keySet());
        List<EventSimilarity> allSimilarities = eventSimilarityRepository
                .findByEvent1InOrEvent2InOrderBySimilarityDesc(candidateIds);

        Map<Long, List<EventSimilarity>> similaritiesByCandidate = new HashMap<>();
        for (EventSimilarity sim : allSimilarities) {
            Long candidateId = candidateIds.stream()
                    .filter(id -> sim.getEvent1().equals(id) || sim.getEvent2().equals(id))
                    .findFirst()
                    .orElse(null);
            if (candidateId != null) {
                similaritiesByCandidate.computeIfAbsent(candidateId, k -> new ArrayList<>()).add(sim);
            }
        }

        Map<Long, Float> userRatings = userActions.stream()
                .collect(Collectors.toMap(
                        UserAction::getEventId,
                        UserAction::getRating,
                        (existing, replacement) -> existing
                ));

        List<RecommendedEventProto> result = new ArrayList<>();

        for (Map.Entry<Long, Float> entry : candidateScores.entrySet()) {
            Long candidateId = entry.getKey();

            List<EventSimilarity> nearEvent = similaritiesByCandidate
                    .getOrDefault(candidateId, Collections.emptyList())
                    .stream()
                    .filter(sim -> {
                        Long neighborId = sim.getEvent1().equals(candidateId)
                                ? sim.getEvent2()
                                : sim.getEvent1();
                        return interactedSet.contains(neighborId);
                    })
                    .limit(20)
                    .collect(Collectors.toList());

            if (nearEvent.isEmpty()) {
                continue;
            }

            double weightedSum = 0.0;
            double similaritySum = 0.0;

            for (EventSimilarity neighbor : nearEvent) {
                Long neighborId = neighbor.getEvent1().equals(candidateId)
                        ? neighbor.getEvent2()
                        : neighbor.getEvent1();

                Float rating = userRatings.getOrDefault(neighborId, 0.0f);
                weightedSum += neighbor.getSimilarity() * rating;
                similaritySum += neighbor.getSimilarity();
            }

            double predictedScore = similaritySum > 0 ? weightedSum / similaritySum : 0.0;

            result.add(RecommendedEventProto.newBuilder()
                    .setEventId(candidateId.intValue())
                    .setScore(predictedScore)
                    .build());
        }

        return result.stream()
                .sorted((p1, p2) -> Double.compare(p2.getScore(), p1.getScore()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    public List<RecommendedEventProto> getSimilarEvents(Long userId, Long eventId, int maxResults) {
        log.debug("Получение похожих событий для eventId: {}, maxResults: {}", eventId, maxResults);

        Pageable pageable = PageRequest.of(0, maxResults);
        List<EventSimilarity> similarEvents = eventSimilarityRepository
                .findByEvent1OrEvent2OrderBySimilarityDesc(eventId, pageable);

        if (similarEvents.isEmpty()) {
            log.info("Похожие события для eventId {} не найдены", eventId);
            return Collections.emptyList();
        }

        List<UserAction> userActions = userActionRepository.findAllByUserId(userId);
        Set<Long> interactedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());

        List<RecommendedEventProto> result = new ArrayList<>();
        for (EventSimilarity sim : similarEvents) {
            Long similarEventId = sim.getEvent1().equals(eventId)
                    ? sim.getEvent2()
                    : sim.getEvent1();

            if (!interactedEvents.contains(similarEventId)) {
                result.add(RecommendedEventProto.newBuilder()
                        .setEventId(similarEventId.intValue())
                        .setScore(sim.getSimilarity())
                        .build());
            }
        }

        log.info("Найдено {} похожих событий для eventId {}", result.size(), eventId);
        return result;
    }

    public List<RecommendedEventProto> getInteractionsCount(List<Integer> eventIdList) {
        if (eventIdList.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Получение количества взаимодействий для {} событий", eventIdList.size());

        List<Object[]> results = userActionRepository.getInteractionsCountForEvents(eventIdList);

        if (results.isEmpty()) {
            log.info("Взаимодействия для событий {} не найдены", eventIdList);
            return Collections.emptyList();
        }

        List<RecommendedEventProto> result = results.stream()
                .map(data -> {
                    Integer eventId = (Integer) data[0];
                    Double totalScore = (Double) data[1];
                    return RecommendedEventProto.newBuilder()
                            .setEventId(eventId)
                            .setScore(totalScore)
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Найдены взаимодействия для {} событий", result.size());
        return result;
    }
}