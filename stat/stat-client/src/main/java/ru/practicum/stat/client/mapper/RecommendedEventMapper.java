package ru.practicum.stat.client.mapper;

import ru.practicum.dto.statServerDto.RecommendedEventDto;
import stats.service.dashboard.RecommendedEventProto;

public class RecommendedEventMapper {
    public static RecommendedEventDto toDto(RecommendedEventProto proto) {
        if (proto == null) {
            return null;
        }

        RecommendedEventDto dto = new RecommendedEventDto();
        dto.setEventId(proto.getEventId());
        dto.setScore(proto.getScore());
        return dto;
    }
}
