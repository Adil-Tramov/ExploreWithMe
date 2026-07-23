package ru.practicum.event.service;

import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.eventDto.UpdateEventAdminRequest;
import ru.practicum.event.model.Event;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminEventService {

    List<Event> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                               LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size);

    Event updateAdminEvent(Long eventId, UpdateEventAdminRequest dto);
}