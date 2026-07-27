package ru.practicum.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateResult;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.faign.EventServiceFeign;
import ru.practicum.faign.UserServiceFeign;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.service.RequestService;
import ru.practicum.controllerInterface.StatClientController;
import stats.service.collector.ActionTypeProto;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users/{userId}/requests")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateRequestController {

    private final RequestService requestService;
    private final UserServiceFeign userServiceFeign;
    private final EventServiceFeign eventServiceFeign;
    private final StatClientController statClient;

    @GetMapping
    public List<ParticipationRequestDto> getUserRequests(@PathVariable @Positive Long userId) {
        log.info("GET /users/{}/requests", userId);

        userServiceFeign.getUser(userId);

        return requestService.getUserRequests(userId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable @Positive Long userId,
                                                 @RequestParam @Positive Long eventId) {
        log.info("POST /users/{}/requests?eventId={}", userId, eventId);

        UserDto requester = userServiceFeign.getUser(userId);
        EventFullDto event = eventServiceFeign.getEventByIdWithoutHttp(eventId);

        ParticipationRequest pr = requestService.createRequest(userId, eventId, event, requester);
        statClient.saveStat(userId, eventId, ActionTypeProto.ACTION_REGISTER);
        return RequestMapper.toDto(pr);
    }

    @PatchMapping("/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable @Positive Long userId,
                                                 @PathVariable @Positive Long requestId) {
        log.info("PATCH /users/{}/requests/{}/cancel", userId, requestId);

        ParticipationRequest pr = requestService.cancelRequest(userId, requestId);
        return RequestMapper.toDto(pr);
    }

    @GetMapping("/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                          @PathVariable @Positive Long eventId) {
        log.info("GET /users/{}/events/{}/requests", userId, eventId);

        EventFullDto event = eventServiceFeign.getEventByIdWithoutHttp(eventId);

        return requestService.getEventRequests(userId, eventId, event).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{eventId}/requests")
    public EventRequestStatusUpdateResult updateEventRequestsStatus(@PathVariable @Positive Long userId,
                                                                    @PathVariable @Positive Long eventId,
                                                                    @RequestBody EventRequestStatusUpdateRequest updateRequest) {
        log.info("PATCH /users/{}/events/{}/requests с телом: {}", userId, eventId, updateRequest);

        EventFullDto event = eventServiceFeign.getEventByIdWithoutHttp(eventId);

        return requestService.updateEventRequestsStatus(userId, eventId, updateRequest, event);
    }

    @GetMapping("/{eventId}")
    boolean confirmUserRegisterOnEvent(@PathVariable @Positive Long userId,
                                       @PathVariable @Positive Long eventId,
                                       @RequestParam("status") RequestStatus requestStatus) {
        log.info("GET /users/{}/events/{}?RequestStatus={}", userId, eventId, requestStatus);
        return requestService.confirmUserRegisterOnEvent(userId, eventId, requestStatus);
    }

    @GetMapping("/allWithStatus/list")
    List<ParticipationRequestDto> getAllByEventIdInAndStatus(
            @PathVariable Long userId,
            @RequestParam("eventIds") List<Long> eventIds,
            @RequestParam("status") RequestStatus requestStatus) {
        log.info("GET /users/{}/requests/allWithStatus/list?RequestStatus={} Event list: {}", userId, requestStatus, eventIds);
        return requestService.getAllByEventIdInAndStatus(userId, eventIds, requestStatus).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }
}