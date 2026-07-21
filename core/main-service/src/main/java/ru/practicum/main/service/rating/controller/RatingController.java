package ru.practicum.main.service.rating.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.service.rating.dto.*;
import ru.practicum.main.service.rating.service.RatingService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
@Slf4j
public class RatingController {

    private final RatingService ratingService;

    @PostMapping("/private/events/{userId}/{eventId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingResponseDto addLike(@RequestBody @Valid EventRatingRequestDto dto,
                                          @PathVariable @Positive Long userId,
                                          @PathVariable @Positive Long eventId) {
        log.info("POST /private/events/{}/{}/like", userId, eventId);
        return ratingService.addLike(userId, eventId, dto);
    }

    @PostMapping("/private/events/{userId}/{eventId}/dislike")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingResponseDto addDislike(@RequestBody @Valid EventRatingRequestDto dto,
                                             @PathVariable @Positive Long userId,
                                             @PathVariable @Positive Long eventId) {
        log.info("POST /private/events/{}/{}/dislike", userId, eventId);
        return ratingService.addDislike(userId, eventId, dto);
    }

    @DeleteMapping("/private/events/{userId}/{eventId}/deleteRating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long eventId) {
        log.info("DELETE /private/events/{}/{}/deleteRating", userId, eventId);
        ratingService.deleteRating(userId, eventId);
    }

    @GetMapping("/public/events/{eventId}/eventRating")
    @ResponseStatus(HttpStatus.OK)
    public EventRatingStatsDto getEventRatingStats(@PathVariable @Positive Long eventId) {
        log.info("GET /public/events/{}/eventRating", eventId);
        return ratingService.getEventRatingStats(eventId);
    }

    @GetMapping("/public/events/{userId}/userRating")
    @ResponseStatus(HttpStatus.OK)
    public EventRatingListDto getUserRatings(
            @PathVariable @Positive Long userId,
            @RequestParam(required = false) String rating,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /public/events/{}/userRating?rating={}&from={}&size={}", userId, rating, from, size);
        return ratingService.getUserRatings(userId, rating, from, size);
    }

    @GetMapping("/public/events/rating")
    @ResponseStatus(HttpStatus.OK)
    public List<EventRatingTopDto> getTopRatedEvents(
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "DESC") String order) {
        log.info("GET /public/events/rating?from={}&size={}&order={}", from, size, order);
        return ratingService.getTopRatedEvents(from, size, order);
    }
}