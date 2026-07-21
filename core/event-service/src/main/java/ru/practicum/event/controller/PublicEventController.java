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
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.PublicEventService;

import java.time.LocalDateTime;
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

        Map<Long, Long> viewsMap = publicEventService.getViewsForEvents(events);

        Map<Long, UserShortDto> initiatorMap = publicEventService.getEventInitiators(events);

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                    return EventMapper.toShortDto(event, confirmedRequests, views, initiator);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable @Positive Long id, HttpServletRequest request) {
        log.info("GET /events/{} - получение события", id);

        Event event = publicEventService.getPublicEventById(id, request);

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Long views = publicEventService.getViewsForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        return EventMapper.toFullDto(event, confirmedRequests, views, initiator);
    }

    @GetMapping("/{id}/WithoutHttp")
    public EventFullDto getEventByIdWithoutHttp(@PathVariable @Positive Long id) {
        log.info("GET /events/{} - получение события без учёта в статистику", id);

        Event event = publicEventService.getPublicEventByIdWithoutHttp(id);

        Long confirmedRequests = publicEventService.getConfirmedRequestsCount(id);
        Long views = publicEventService.getViewsForEvent(event);
        UserShortDto initiator = publicEventService.getEventInitiator(event);

        return EventMapper.toFullDto(event, confirmedRequests, views, initiator);
    }
}