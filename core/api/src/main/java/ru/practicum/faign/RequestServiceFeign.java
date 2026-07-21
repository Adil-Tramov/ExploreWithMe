package ru.practicum.faign;

import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateResult;
import ru.practicum.dto.requestDto.ParticipationRequestDto;
import ru.practicum.dto.requestDto.RequestStatus;

import java.util.List;

@FeignClient(name = "request-service", configuration = FeignConfig.class)
public interface RequestServiceFeign {

    @GetMapping("/users/{userId}/requests/{eventId}/requests")
    List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                   @PathVariable @Positive Long eventId);

    @PostMapping("/users/{userId}/requests/{eventId}/requests")
    EventRequestStatusUpdateResult updateEventRequestsStatus(@PathVariable @Positive Long userId,
                                                             @PathVariable @Positive Long eventId,
                                                             @RequestBody EventRequestStatusUpdateRequest updateRequest);

    @GetMapping("/users/{userId}/requests/{eventId}")
    boolean confirmUserRegisterOnEvent(@PathVariable @Positive Long userId,
                                       @PathVariable @Positive Long eventId,
                                       @RequestParam("status") RequestStatus requestStatus);

    @GetMapping("/users/{userId}/requests/allWithStatus/list")
    List<ParticipationRequestDto> getAllByEventIdInAndStatus(
            @PathVariable Long userId,
            @RequestParam("eventIds") List<Long> eventIds,
            @RequestParam("status") RequestStatus requestStatus);
}
