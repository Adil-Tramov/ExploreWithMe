package ru.practicum.dto.ratingDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRatingResponseDto {
    private Long id;
    private Long eventId;
    private Long userId;

    @JsonProperty("rating")
    private String ratingType;

    private LocalDateTime timestamp;
}
