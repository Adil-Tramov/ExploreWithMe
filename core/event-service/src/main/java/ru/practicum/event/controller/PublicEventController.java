package ru.practicum.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.statServerDto.RecommendedEventDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.PublicEventService;
import ru.practicum.controllerInterface.StatClientController;
import stats.service.collector.ActionTypeProto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PublicEventController {

    private final PublicEventService publicEventService;
    private final StatClientController statClient;

    @GetMapping
    public List<EventShortDto> getEvents(@RequestParam(required = false) String text,
                                         @RequestParam(required = false) List<Long> categories,
                                         @RequestParam(required = false) Boolean paid,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
                                         @RequestParam(defaultValue = "false") Boolean onlyAvailable,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(defaultValue = "0") int from,
                                         @RequestParam(defaultValue = "10") int size,
                                         HttpServletRequest request) {
        log.info("GET /events - публичный поиск событий");

        List<Event> events = publicEventService.getPublicEvents(text, categories, paid, rangeStart,
                rangeEnd, onlyAvailable, sort, from, size, request);

        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(
                events.stream().map(Event::getId).toList()
        );

        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();
        List<RecommendedEventDto> rating = statClient.getInteractionsCountAsList(eventIds);
        Map<Long, Double> ratingForEventMap = new HashMap<>();
        rating.forEach(recommendedEventProto -> {
                    ratingForEventMap.putIfAbsent((long) recommendedEventProto.getEventId(), recommendedEventProto.getScore());
                }
        );

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                    return EventMapper.toShortDto(event, confirmedRequests, ratingForEventMap.get(event.getId()), initiator);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable @Positive Long id, @RequestHeader("X-EWM-USER-ID") long userId) {
        log.info("GET /events/{} - получение события", id);

        Event event = publicEventService.getPublicEventById(id, userId);

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        List<RecommendedEventDto> rating = statClient.getInteractionsCountAsList(List.of(event.getId()));

        statClient.saveStat(userId, event.getId(), ActionTypeProto.ACTION_VIEW);

        return EventMapper.toFullDto(event, confirmedRequests, rating.getFirst().getScore(), initiator);
    }

    @GetMapping("/{id}/WithoutHttp")
    public EventFullDto getEventByIdWithoutHttp(@PathVariable @Positive Long id) {
        log.info("GET /events/{} - получение события без учёта в статистику", id);

        Event event = publicEventService.getPublicEventByIdWithoutHttp(id);

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        List<RecommendedEventDto> rating = statClient.getInteractionsCountAsList(List.of(event.getId()));

        return EventMapper.toFullDto(event, confirmedRequests, rating.getFirst().getScore(), initiator);
    }

    @GetMapping("/recommendations")
    public List<EventFullDto> getRecommendationForUser(@RequestHeader("X-EWM-USER-ID") long userId) {
        log.info("GET /events//recommendations - получение списка рекомендованные мероприятий для пользователя с id: {}", userId);
        List<RecommendedEventDto> recommendationList = statClient.getRecommendationsForUserAsList(userId, 20);
        Map<Long, Double> recommendationMap = new HashMap<>();
        recommendationList.forEach(recommendedEventProto -> {
            recommendationMap.putIfAbsent((long) recommendedEventProto.getEventId(), recommendedEventProto.getScore());
        });
        List<Event> events = publicEventService.findAllEventsByEventId(recommendationMap.keySet());

        Map<Long, Long> confirmedMap = publicEventService.getConfirmedRequestsCounts(
                events.stream().map(Event::getId).toList()
        );
        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                    return EventMapper.toFullDto(event, confirmedRequests, recommendationMap.get(event.getId()), initiator);
                })
                .collect(Collectors.toList());
    }

    @PutMapping("/{eventId}/like")
    public void sendLikeForEvent(@PathVariable @Positive Long eventId,
                                 @RequestHeader("X-EWM-USER-ID") long userId) {
        log.info("PUT /events/{eventId}/like - Поставить like мероприятию [{}] с id: {}", eventId, userId);
        statClient.saveStat(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }
}