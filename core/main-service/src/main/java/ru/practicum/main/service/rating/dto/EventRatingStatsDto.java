package ru.practicum.main.service.rating.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRatingStatsDto {
    private Long eventId;

    @JsonProperty("like_count")
    private Long likeCount;

    @JsonProperty("dislike_count")
    private Long dislikeCount;
}