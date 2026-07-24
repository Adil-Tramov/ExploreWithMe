package stats.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        List<UserAction> userActions = userActionRepository
                .findAllByUserIdOrderByTsDesc(userId);

        List<Long> recentInteractedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .limit(maxResults)
                .collect(Collectors.toList());

        if (recentInteractedEvents.isEmpty()) {
            log.info("У пользователя ID: {} нет взаимодействий с событиями", userId);
            return Collections.emptyList();
        }

        List<EventSimilarity> similarEvents = eventSimilarityRepository
                .findByEvent1InOrEvent2InOrderBySimilarityDesc(recentInteractedEvents);

        Map<Long, Float> recommendedEvents = new LinkedHashMap<>();

        for (EventSimilarity event : similarEvents) {
            Long event1 = event.getEvent1();
            Long event2 = event.getEvent2();
            Float score = event.getSimilarity();

            boolean hasEvent1 = recentInteractedEvents.contains(event1);
            boolean hasEvent2 = recentInteractedEvents.contains(event2);

            if (hasEvent1 != hasEvent2) {
                Long candidateEventId = hasEvent1 ? event2 : event1;
                if (!recentInteractedEvents.contains(candidateEventId)) {
                    recommendedEvents.putIfAbsent(candidateEventId, score);
                }
            }
        }

        if (recommendedEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> candidateEventIds = new ArrayList<>(recommendedEvents.keySet());
        List<EventSimilarity> allSimilarities = eventSimilarityRepository
                .findByEvent1InOrEvent2InOrderBySimilarityDesc(candidateEventIds);

        Map<Long, List<EventSimilarity>> similaritiesByCandidate = new HashMap<>();
        for (EventSimilarity sim : allSimilarities) {
            Long candidateId = candidateEventIds.stream()
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

        for (Map.Entry<Long, Float> entry : recommendedEvents.entrySet()) {
            Long candidateEventId = entry.getKey();

            List<EventSimilarity> nearEvent = similaritiesByCandidate
                    .getOrDefault(candidateEventId, Collections.emptyList())
                    .stream()
                    .filter(sim -> {
                        Long neighborId = sim.getEvent1().equals(candidateEventId)
                                ? sim.getEvent2()
                                : sim.getEvent1();
                        return recentInteractedEvents.contains(neighborId);
                    })
                    .limit(20)
                    .collect(Collectors.toList());

            if (nearEvent.isEmpty()) {
                continue;
            }

            double weightedSum = 0.0;
            double similaritySum = 0.0;

            for (EventSimilarity neighbor : nearEvent) {
                Long neighborId = neighbor.getEvent1().equals(candidateEventId)
                        ? neighbor.getEvent2()
                        : neighbor.getEvent1();

                float rating = userRatings.getOrDefault(neighborId, 0.0f);
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

    public List<RecommendedEventProto> getSimilarEvents(Long userId, Long eventId, int maxResults) {
        List<UserAction> userActions = userActionRepository.findAllByUserId(userId);
        List<Long> interactedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toList());

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

        return result;
    }

    public List<RecommendedEventProto> getInteractionsCount(List<Integer> eventIdList) {
        if (eventIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = eventIdList.stream()
                .map(Integer::longValue)
                .collect(Collectors.toList());

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

        return eventScore.entrySet().stream()
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey().intValue())
                        .setScore(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}