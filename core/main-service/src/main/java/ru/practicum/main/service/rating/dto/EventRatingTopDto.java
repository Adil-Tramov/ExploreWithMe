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
public class EventRatingTopDto {
    private Long eventId;

    @JsonProperty("rating_count")
    private Long ratingCount;
}