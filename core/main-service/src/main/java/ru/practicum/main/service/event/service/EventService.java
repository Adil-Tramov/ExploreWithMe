package ru.practicum.main.service.event.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.main.service.event.dto.*;
import ru.practicum.main.service.event.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {

    // Приватные
    EventFullDto createEvent(Long userId, NewEventDto dto);

    List<EventShortDto> getUserEvents(Long userId, int from, int size);

    EventFullDto getUserEventById(Long userId, Long eventId);

    EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto);

    // Публичные
    List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                        Boolean onlyAvailable, String sort, int from, int size,
                                        HttpServletRequest request);

    EventFullDto getPublicEventById(Long eventId, HttpServletRequest request);

    // Админские
    List<EventFullDto> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                                      LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size);

    EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest dto);
}
