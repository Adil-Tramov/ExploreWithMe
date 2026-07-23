package ru.practicum.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.ratingDto.*;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.faign.EventServiceFeign;
import ru.practicum.faign.RequestServiceFeign;
import ru.practicum.faign.UserServiceFeign;
import ru.practicum.mapper.RatingMapper;
import ru.practicum.model.EventRating;
import ru.practicum.service.RatingService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
@Slf4j
public class RatingController {

    private final RatingService ratingService;
    private final UserServiceFeign userServiceFeign;
    private final EventServiceFeign eventServiceFeign;
    private final RequestServiceFeign requestServiceFeign;

    @PostMapping("/private/events/{userId}/{eventId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingResponseDto addLike(@RequestBody @Valid EventRatingRequestDto dto,
                                          @PathVariable @Positive Long userId,
                                          @PathVariable @Positive Long eventId) {
        log.info("POST /private/events/{}/{}/like", userId, eventId);

        UserDto user = userServiceFeign.getUser(userId);

        EventFullDto event = eventServiceFeign.getEventByIdWithoutHttp(eventId);

        boolean userAttended = requestServiceFeign.confirmUserRegisterOnEvent(
                userId, eventId, RequestStatus.CONFIRMED);

        EventRating rating = ratingService.addLike(userId, eventId, dto, event, userAttended);

        return RatingMapper.toResponseDto(rating);
    }

    @PostMapping("/private/events/{userId}/{eventId}/dislike")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingResponseDto addDislike(@RequestBody @Valid EventRatingRequestDto dto,
                                             @PathVariable @Positive Long userId,
                                             @PathVariable @Positive Long eventId) {
        log.info("POST /private/events/{}/{}/dislike", userId, eventId);

        UserDto user = userServiceFeign.getUser(userId);

        EventFullDto event = eventServiceFeign.getEventByIdWithoutHttp(eventId);

        boolean userAttended = requestServiceFeign.confirmUserRegisterOnEvent(
                userId, eventId, RequestStatus.CONFIRMED);

        EventRating rating = ratingService.addDislike(userId, eventId, dto, event, userAttended);

        return RatingMapper.toResponseDto(rating);
    }

    @DeleteMapping("/private/events/{userId}/{eventId}/deleteRating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(@PathVariable @Positive Long userId,
                             @PathVariable @Positive Long eventId) {
        log.info("DELETE /private/events/{}/{}/deleteRating", userId, eventId);

        userServiceFeign.getUser(userId);

        ratingService.deleteRating(userId, eventId);
    }

    @GetMapping("/public/events/{eventId}/eventRating")
    public EventRatingStatsDto getEventRatingStats(@PathVariable @Positive Long eventId) {
        log.info("GET /public/events/{}/eventRating", eventId);

        eventServiceFeign.getEventByIdWithoutHttp(eventId);

        return ratingService.getEventRatingStats(eventId);
    }

    @GetMapping("/public/events/{userId}/userRating")
    public EventRatingListDto getUserRatings(@PathVariable @Positive Long userId,
                                             @RequestParam(required = false) String rating,
                                             @RequestParam(defaultValue = "0") int from,
                                             @RequestParam(defaultValue = "10") int size) {
        log.info("GET /public/events/{}/userRating?rating={}&from={}&size={}", userId, rating, from, size);

        userServiceFeign.getUser(userId);

        return ratingService.getUserRatings(userId, rating, from, size);
    }

    @GetMapping("/public/events/rating")
    public List<EventRatingTopDto> getTopRatedEvents(@RequestParam(defaultValue = "0") int from,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(defaultValue = "DESC") String order) {
        log.info("GET /public/events/rating?from={}&size={}&order={}", from, size, order);

        List<Object[]> topData = ratingService.getTopRatedEvents(from, size, order);

        return RatingMapper.toTopDtoList(topData);
    }
}