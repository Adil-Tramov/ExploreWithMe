package ru.practicum.stat.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stat.dto.ViewStatsDto;
import ru.practicum.stat.server.dto.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

public interface StatServerRepository extends JpaRepository<EndpointHit, Long> {

    @Query("SELECT new ru.practicum.stat.dto.ViewStatsDto(e.app, e.uri, " +
            "CASE WHEN :unique = true THEN COUNT(DISTINCT e.ip) ELSE COUNT(e.id) END) " +
            "FROM EndpointHit e " +
            "WHERE e.timestamp BETWEEN :start AND :end " +
            "GROUP BY e.app, e.uri " +
            "ORDER BY 3 DESC")
    List<ViewStatsDto> findStats(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("unique") boolean unique);

    @Query("SELECT new ru.practicum.stat.dto.ViewStatsDto(e.app, e.uri, " +
            "CASE WHEN :unique = true THEN COUNT(DISTINCT e.ip) ELSE COUNT(e.id) END) " +
            "FROM EndpointHit e " +
            "WHERE e.timestamp BETWEEN :start AND :end " +
            "AND e.uri IN :uris " +
            "GROUP BY e.app, e.uri " +
            "ORDER BY 3 DESC")
    List<ViewStatsDto> findStatsByUris(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("uris") List<String> uris,
                                       @Param("unique") boolean unique);

}