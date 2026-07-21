package ru.practicum.service;

import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.ratingDto.EventRatingListDto;
import ru.practicum.dto.ratingDto.EventRatingRequestDto;
import ru.practicum.dto.ratingDto.EventRatingStatsDto;
import ru.practicum.model.EventRating;

import java.util.List;

public interface RatingService {

    EventRating addLike(Long userId, Long eventId, EventRatingRequestDto dto,
                        EventFullDto event, boolean userAttended);

    EventRating addDislike(Long userId, Long eventId, EventRatingRequestDto dto,
                           EventFullDto event, boolean userAttended);

    void deleteRating(Long userId, Long eventId);

    EventRatingStatsDto getEventRatingStats(Long eventId);

    EventRatingListDto getUserRatings(Long userId, String rating, int from, int size);

    List<Object[]> getTopRatedEvents(int from, int size, String order);
}