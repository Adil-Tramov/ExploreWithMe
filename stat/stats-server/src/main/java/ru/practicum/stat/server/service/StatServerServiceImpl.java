package ru.practicum.stat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;
import ru.practicum.stat.server.dto.EndpointHit;
import ru.practicum.stat.server.dto.EndpointHitMapper;
import ru.practicum.stat.server.repository.StatServerRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatServerServiceImpl implements StatServerService {
    private final StatServerRepository statServerRepository;

    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto dto) {
        EndpointHit eh = EndpointHitMapper.toEntity(dto);
        eh = statServerRepository.save(eh);
        log.debug("Added hit: {}", eh);
        return EndpointHitMapper.toDto(eh);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Время начала поиска не может быть после времени окончания");
        }
        if (uris == null) {
            return statServerRepository.findStats(start, end, unique);
        }
        return statServerRepository.findStatsByUris(start, end, uris, unique);
    }
}
