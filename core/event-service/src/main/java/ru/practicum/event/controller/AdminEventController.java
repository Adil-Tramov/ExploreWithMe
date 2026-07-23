package ru.practicum.event.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.eventDto.UpdateEventAdminRequest;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.AdminEventService;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminEventController {

    private final AdminEventService adminEventService;
    private final UserServiceFeign userServiceFeign;
    private final RequestServiceFeign requestServiceFeign;

    @GetMapping
    public List<EventFullDto> getEvents(@RequestParam(required = false) List<Long> users,
                                        @RequestParam(required = false) List<EventState> states,
                                        @RequestParam(required = false) List<Long> categories,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
                                        @RequestParam(defaultValue = "0") int from,
                                        @RequestParam(defaultValue = "10") int size) {
        log.info("GET /admin/events - админский поиск событий");

        List<Event> events = adminEventService.getAdminEvents(users, states, categories, rangeStart, rangeEnd, from, size);

        if (events.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toList());

        List<UserDto> userDtos = userServiceFeign.getAllUsersById(userIds);

        Map<Long, UserShortDto> userShortMap = userDtos.stream()
                .collect(Collectors.toMap(
                        UserDto::getId,
                        user -> new UserShortDto(user.getId(), user.getName())
                ));

        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(events);

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = adminEventService.getViewsForEvent(event);
                    UserShortDto initiator = userShortMap.get(event.getInitiatorId());
                    return EventMapper.toFullDto(event, confirmedRequests, views, initiator);
                })
                .collect(Collectors.toList());
    }

    @PatchMapping("/{eventId}")
    public EventFullDto updateEvent(@PathVariable @Positive Long eventId,
                                    @RequestBody @Valid UpdateEventAdminRequest dto) {
        log.info("PATCH /admin/events/{} - админское обновление события: {}", eventId, dto);

        Event updatedEvent = adminEventService.updateAdminEvent(eventId, dto);

        UserDto user = userServiceFeign.getUser(updatedEvent.getInitiatorId());
        UserShortDto userShortDto = new UserShortDto(user.getId(), user.getName());

        Long confirmedRequests = getConfirmedRequestsCount(eventId);
        Long views = adminEventService.getViewsForEvent(updatedEvent);

        EventFullDto eventFullDto = EventMapper.toFullDto(updatedEvent, confirmedRequests, views, userShortDto);
        log.info("Результат обновления: {}", eventFullDto);
        return eventFullDto;
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