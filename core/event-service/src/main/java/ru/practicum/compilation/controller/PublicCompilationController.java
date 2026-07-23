package ru.practicum.compilation.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.service.PublicCompilationService;
import ru.practicum.dto.compilationDto.CompilationDto;
import ru.practicum.dto.eventDto.EventShortDto;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.dto.userDto.UserShortDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/compilations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PublicCompilationController {

    private final PublicCompilationService publicCompilationService;
    private final UserServiceFeign userServiceFeign;
    private final RequestServiceFeign requestServiceFeign;

    @GetMapping
    public List<CompilationDto> getCompilations(@RequestParam(required = false) Boolean pinned,
                                                @RequestParam(defaultValue = "0") int from,
                                                @RequestParam(defaultValue = "10") int size) {
        log.info("GET /compilations - получение подборок");

        List<Compilation> compilations = publicCompilationService.getCompilations(pinned, from, size);

        if (compilations.isEmpty()) {
            return List.of();
        }

        List<Event> allEvents = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .distinct()
                .toList();

        Map<Long, Long> viewsMap = publicCompilationService.getViewsForEvents(allEvents);

        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(allEvents);

        Map<Long, UserShortDto> initiatorMap = getEventInitiators(allEvents);

        return compilations.stream()
                .map(compilation -> {
                    List<EventShortDto> eventShortDtos = compilation.getEvents().stream()
                            .map(event -> {
                                Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                                Long views = viewsMap.getOrDefault(event.getId(), 0L);
                                UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                                return EventMapper.toShortDto(event, confirmedRequests, views, initiator);
                            })
                            .collect(Collectors.toList());

                    return CompilationMapper.toDto(compilation, eventShortDtos);
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/{compId}")
    public CompilationDto getCompilationById(@PathVariable @Positive Long compId) {
        log.info("GET /compilations/{} - получение подборки", compId);

        Compilation compilation = publicCompilationService.getCompilationById(compId);

        List<Event> events = compilation.getEvents();

        Map<Long, Long> viewsMap = publicCompilationService.getViewsForEvents(events);

        Map<Long, Long> confirmedMap = getConfirmedRequestsCounts(events);

        Map<Long, UserShortDto> initiatorMap = getEventInitiators(events);

        List<EventShortDto> eventShortDtos = events.stream()
                .map(event -> {
                    Long confirmedRequests = confirmedMap.getOrDefault(event.getId(), 0L);
                    Long views = viewsMap.getOrDefault(event.getId(), 0L);
                    UserShortDto initiator = initiatorMap.get(event.getInitiatorId());

                    return EventMapper.toShortDto(event, confirmedRequests, views, initiator);
                })
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, eventShortDtos);
    }


    private Map<Long, Long> getConfirmedRequestsCounts(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        try {
            return requestServiceFeign
                    .getAllByEventIdInAndStatus(1L, eventIds, RequestStatus.CONFIRMED)
                    .stream()
                    .collect(Collectors.groupingBy(
                            ParticipationRequestDto::getEvent,
                            Collectors.counting()
                    ));
        } catch (Exception e) {
            log.warn("Не удалось получить количество подтвержденных запросов: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, UserShortDto> getEventInitiators(List<Event> events) {
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
            log.warn("Не удалось получить данные пользователей для событий: {}", e.getMessage());
            return Map.of();
        }
    }
}