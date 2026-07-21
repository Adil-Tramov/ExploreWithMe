package ru.practicum.main.service.rating.service;

import ru.practicum.main.service.rating.dto.*;

import java.util.List;

public interface RatingService {

    EventRatingResponseDto addLike(Long userId, Long eventId, EventRatingRequestDto dto);

    EventRatingResponseDto addDislike(Long userId, Long eventId, EventRatingRequestDto dto);

    void deleteRating(Long userId, Long eventId);

    EventRatingStatsDto getEventRatingStats(Long eventId);

    EventRatingListDto getUserRatings(Long userId, String rating, int from, int size);

    List<EventRatingTopDto> getTopRatedEvents(int from, int size, String order);
}