package ru.practicum.event.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.eventDto.NewEventDto;
import ru.practicum.dto.eventDto.UpdateEventUserRequest;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateResult;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.PrivateEventService;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users/{userId}/events")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateEventController {

    private final PrivateEventService privateEventService;
    private final RequestServiceFeign requestServiceFeign;
    private final UserServiceFeign userServiceFeign;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable @Positive Long userId,
                                    @Valid @RequestBody NewEventDto dto) {
        log.info("POST /users/{}/events - создание события: {}", userId, dto);

        UserDto user = userServiceFeign.getUser(userId);
        Event event = privateEventService.createEvent(userId, dto, user);

        return EventMapper.toFullDto(event, 0L, 0L,
                new UserShortDto(user.getId(), user.getName()));
    }

    @GetMapping
    public List<EventShortDto> getUserEvents(@PathVariable @Positive Long userId,
                                             @RequestParam(defaultValue = "0") int from,
                                             @RequestParam(defaultValue = "10") int size) {
        log.info("GET /users/{}/events - получение событий пользователя", userId);

        UserDto user = userServiceFeign.getUser(userId);
        List<Event> events = privateEventService.getUserEvents(userId, from, size);

        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(events);

        Map<Long, Long> viewsMap = privateEventService.getViewsForEvents(events);

        UserShortDto initiator = new UserShortDto(user.getId(), user.getName());

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    return EventMapper.toShortDto(event, confirmedRequests, views, initiator);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{eventId}")
    public EventFullDto getUserEventById(@PathVariable @Positive Long userId,
                                         @PathVariable @Positive Long eventId) {
        log.info("GET /users/{}/events/{} - получение события", userId, eventId);

        UserDto user = userServiceFeign.getUser(userId);
        Event event = privateEventService.getUserEventById(userId, eventId);

        Long confirmedRequests = getConfirmedRequestsCount(eventId);

        Long views = privateEventService.getViewsForEvent(event);

        return EventMapper.toFullDto(event, confirmedRequests, views,
                new UserShortDto(user.getId(), user.getName()));
    }

    @PatchMapping("/{eventId}")
    public EventFullDto updateUserEvent(@PathVariable @Positive Long userId,
                                        @PathVariable @Positive Long eventId,
                                        @Valid @RequestBody UpdateEventUserRequest dto) {
        log.info("PATCH /users/{}/events/{} - обновление события: {}", userId, eventId, dto);

        UserDto user = userServiceFeign.getUser(userId);
        Event event = privateEventService.updateUserEvent(userId, eventId, dto);

        Long confirmedRequests = getConfirmedRequestsCount(eventId);

        Long views = privateEventService.getViewsForEvent(event);

        return EventMapper.toFullDto(event, confirmedRequests, views,
                new UserShortDto(user.getId(), user.getName()));
    }

    @GetMapping("/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                          @PathVariable @Positive Long eventId) {
        log.info("GET /users/{}/events/{}/requests", userId, eventId);
        return requestServiceFeign.getEventRequests(userId, eventId);
    }

    @PatchMapping("/{eventId}/requests")
    public EventRequestStatusUpdateResult updateEventRequestsStatus(@PathVariable @Positive Long userId,
                                                                    @PathVariable @Positive Long eventId,
                                                                    @RequestBody EventRequestStatusUpdateRequest updateRequest) {
        log.info("PATCH /users/{}/events/{}/requests: {}", userId, eventId, updateRequest);
        return requestServiceFeign.updateEventRequestsStatus(userId, eventId, updateRequest);
    }


    private Long getConfirmedRequestsCount(Long eventId) {
        return (long) requestServiceFeign.getAllByEventIdInAndStatus(1L, List.of(eventId), RequestStatus.CONFIRMED).size();
    }

    private Map<Long, Long> getConfirmedRequestsCounts(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        return requestServiceFeign
                .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        ParticipationRequestDto::getEvent,
                        Collectors.counting()
                ));
    }
}