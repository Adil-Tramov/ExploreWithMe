package ru.practicum.stat.server.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.stat.dto.EndpointHitDto;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EndpointHitMapper {

    public static EndpointHit toEntity(EndpointHitDto dto) {
        return new EndpointHit(dto.getId(),
                dto.getApp(),
                dto.getUri(),
                dto.getIp(),
                dto.getTimestamp());
    }

    public static EndpointHitDto toDto(EndpointHit eh) {
        return new EndpointHitDto(eh.getId(),
                eh.getApp(),
                eh.getUri(),
                eh.getIp(),
                eh.getTimestamp());
    }
}
