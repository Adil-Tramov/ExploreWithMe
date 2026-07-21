package ru.practicum.event.service;

import ru.practicum.dto.eventDto.NewEventDto;
import ru.practicum.dto.eventDto.UpdateEventUserRequest;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.event.model.Event;

import java.util.List;
import java.util.Map;

public interface PrivateEventService {

    Event createEvent(Long userId, NewEventDto dto, UserDto user);

    List<Event> getUserEvents(Long userId, int from, int size);

    Event getUserEventById(Long userId, Long eventId);

    Event updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto);

    Long getViewsForEvent(Event event);

    Map<Long, Long> getViewsForEvents(List<Event> events);
}