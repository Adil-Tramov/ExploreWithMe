package ru.practicum.main.service.rating.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.main.service.rating.dto.EventRatingResponseDto;
import ru.practicum.main.service.rating.dto.EventRatingStatsDto;
import ru.practicum.main.service.rating.dto.EventRatingTopDto;
import ru.practicum.main.service.rating.model.EventRating;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RatingMapper {

    public static EventRating toEntity(Long eventId, Long userId, EventRating.RatingType type, LocalDateTime time) {
        return EventRating.builder()
                .eventId(eventId)
                .userId(userId)
                .ratingType(type)
                .created(time)
                .build();
    }

    public static EventRatingResponseDto toResponseDto(EventRating rating) {
        return EventRatingResponseDto.builder()
                .id(rating.getId())
                .eventId(rating.getEventId())
                .userId(rating.getUserId())
                .ratingType(rating.getRatingType().name().toLowerCase())
                .timestamp(rating.getCreated())
                .build();
    }

    public static List<EventRatingResponseDto> toResponseDtoList(List<EventRating> ratings) {
        return ratings.stream()
                .map(RatingMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    public static EventRatingStatsDto toStatsDto(Long eventId, Long likeCount, Long dislikeCount) {
        return EventRatingStatsDto.builder()
                .eventId(eventId)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .build();
    }

    public static EventRatingTopDto toTopDto(Long eventId, Long ratingCount) {
        return EventRatingTopDto.builder()
                .eventId(eventId)
                .ratingCount(ratingCount)
                .build();
    }

    public static List<EventRatingTopDto> toTopDtoList(List<Object[]> topData) {
        return topData.stream()
                .map(data -> {
                    Long eventId = ((Number) data[0]).longValue();
                    Long ratingCount = ((Number) data[1]).longValue();
                    return toTopDto(eventId, ratingCount);
                })
                .collect(Collectors.toList());
    }
}