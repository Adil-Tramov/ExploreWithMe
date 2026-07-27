package ru.practicum.event.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicEventServiceImpl implements PublicEventService {

    private final EventRepository eventRepository;
    private final RequestServiceFeign requestServiceFeign;
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

        return applySorting(events, sort);
    }

    @Override
    public Event getPublicEventById(Long eventId, long userId) {
        log.info("Публичное получение события {}", eventId);

        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Ивент с id:" + eventId + " не найден"));

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

    @Override
    public List<Event> findAllEventsByEventId(Set<Long> ids) {
        return eventRepository.findAllById(ids);
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
        if (events == null || events.isEmpty()) {
            return events;
        }

        List<Event> eventsWithoutLimit = new ArrayList<>();
        List<Event> eventsWithLimit = new ArrayList<>();

        for (Event event : events) {
            if (event.getParticipantLimit() == 0) {
                eventsWithoutLimit.add(event);
            } else {
                eventsWithLimit.add(event);
            }
        }

        if (eventsWithLimit.isEmpty()) {
            return eventsWithoutLimit;
        }

        List<Long> eventIdsWithLimit = eventsWithLimit.stream()
                .map(Event::getId)
                .toList();

        Map<Long, Long> confirmedRequestsCount = requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIdsWithLimit, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        request -> request.getEvent(),
                        Collectors.counting()
                ));

        List<Event> filteredWithLimit = eventsWithLimit.stream()
                .filter(event -> {
                    Long confirmed = confirmedRequestsCount.getOrDefault(event.getId(), 0L);
                    return confirmed < event.getParticipantLimit();
                })
                .toList();

        List<Event> result = new ArrayList<>(eventsWithoutLimit);
        result.addAll(filteredWithLimit);

        log.info("Отфильтровано доступных событий: {} из {}", result.size(), events.size());
        return result;
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
}