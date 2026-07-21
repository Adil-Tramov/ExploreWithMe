package ru.practicum.event.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.PublicEventService;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final RequestServiceFeign requestServiceFeign;
    private final StatsClient statsClient;
    private final UserServiceFeign userServiceFeign;

    @Override
    public List<Event> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                       LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                       Boolean onlyAvailable, String sort, int from, int size,
                                       HttpServletRequest request) {
        log.info("Публичный поиск событий");

        validateDateRange(rangeStart, rangeEnd);

        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        Pageable pageable = createPageable(sort, from, size);

        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, start, rangeEnd, pageable);

        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = filterOnlyAvailable(events);
        }

        List<Event> result = applySorting(events, sort);
        saveHit(request);

        return result;
    }

    @Override
    public Event getPublicEventById(Long eventId, HttpServletRequest request) {
        log.info("Публичное получение события {}", eventId);

        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Ивент с id:" + eventId + " не найден"));
        saveHit(request);

        return event;
    }

    @Override
    public Event getPublicEventByIdWithoutHttp(Long eventId) {
        log.info("Публичное получение события без попадания в статистику {}", eventId);
        return findPublishedEventById(eventId);
    }

    @Override
    public Long getConfirmedRequestsCount(Long eventId) {
        return (long) requestServiceFeign.getAllByEventIdInAndStatus(1L, List.of(eventId), RequestStatus.CONFIRMED).size();
    }

    @Override
    public Long getViewsForEvent(Event event) {
        if (event == null) {
            return 0L;
        }

        LocalDateTime start = event.getPublishedOn() != null ?
                event.getPublishedOn() :
                event.getCreatedOn();

        if (start == null) {
            start = LocalDateTime.now().minusYears(10);
        }

        List<String> uris = List.of("/events/" + event.getId());
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);

        return stats.isEmpty() ? 0L : stats.getFirst().getHits();
    }

    @Override
    public Map<Long, Long> getConfirmedRequestsCounts(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        return requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        request -> request.getEvent(),
                        Collectors.counting()
                ));
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        LocalDateTime earliestStart = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .toList();

        List<ViewStatsDto> stats = statsClient.getStats(earliestStart, LocalDateTime.now(), uris, false);

        return stats.stream()
                .collect(Collectors.toMap(
                        v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                        ViewStatsDto::getHits,
                        (a, b) -> a
                ));
    }

    @Override
    public UserShortDto getEventInitiator(Event event) {
        if (event == null) {
            return null;
        }

        try {
            UserDto user = userServiceFeign.getUser(Long.valueOf(event.getInitiatorId()));
            return new UserShortDto(user.getId(), user.getName());
        } catch (Exception e) {
            log.warn("Не удалось получить данные пользователя для события {}", event.getId());
            return null;
        }
    }

    @Override
    public Map<Long, UserShortDto> getEventInitiators(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> userIds = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .toList();

        try {
            List<UserDto> users = userServiceFeign.getAllUsersById(userIds);
            return users.stream()
                    .collect(Collectors.toMap(
                            UserDto::getId,
                            user -> new UserShortDto(user.getId(), user.getName())
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить данные пользователей для событий");
            return Map.of();
        }
    }


    private void validateDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart == null) {
            return;
        }
        if (rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
    }

    private Pageable createPageable(String sort, int from, int size) {
        Sort sortBy;
        if (sort != null && sort.equals("VIEWS")) {
            sortBy = Sort.by(Sort.Direction.DESC, "id");
        } else {
            sortBy = Sort.by(Sort.Direction.ASC, "eventDate");
        }
        return PageRequest.of(from / size, size, sortBy);
    }

    private List<Event> filterOnlyAvailable(List<Event> events) {
        return events.stream()
                .filter(this::isEventAvailable)
                .collect(Collectors.toList());
    }

    private boolean isEventAvailable(Event event) {
        if (event.getParticipantLimit() == 0) {
            return true;
        }
        long confirmed = (long) requestServiceFeign.getAllByEventIdInAndStatus(1L, List.of(event.getId()), RequestStatus.CONFIRMED).size();
        return confirmed < event.getParticipantLimit();
    }

    private List<Event> applySorting(List<Event> events, String sort) {
        if (sort != null && sort.equals("VIEWS")) {
            return events;
        }
        return events;
    }

    private Event findPublishedEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Событие с id=" + eventId + " не опубликовано");
        }

        return event;
    }

    private void saveHit(HttpServletRequest request) {
        EndpointHitDto hit = EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.hit(hit);
    }
}