package ru.practicum.dto.statServerDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedEventDto {
    private Integer eventId;
    private Double score;
}
