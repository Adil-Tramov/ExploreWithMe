package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.model.Event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PublicEventService {

    List<Event> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                Boolean onlyAvailable, String sort, int from, int size,
                                HttpServletRequest request);

    Event getPublicEventById(Long eventId, long userId);

    Event getPublicEventByIdWithoutHttp(Long eventId);

    Long getConfirmedRequestsCount(Long eventId);

    Map<Long, Long> getConfirmedRequestsCounts(List<Long> eventIds);

    UserShortDto getEventInitiator(Event event);

    Map<Long, UserShortDto> getEventInitiators(List<Event> events);

    List<Event> findAllEventsByEventId(Set<Long> ids);
}