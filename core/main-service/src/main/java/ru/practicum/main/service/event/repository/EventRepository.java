package ru.practicum.main.service.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Для админа: поиск с фильтрацией
    @Query("SELECT e FROM Event e WHERE " +
            "(coalesce(:users, null) IS NULL OR e.initiator.id IN :users) AND " +
            "(coalesce(:states, null) IS NULL OR e.state IN :states) AND " +
            "(coalesce(:categories, null) IS NULL OR e.category.id IN :categories) AND " +
            "(coalesce(:rangeStart, null) IS NULL OR e.eventDate >= :rangeStart) AND " +
            "(coalesce(:rangeEnd, null) IS NULL OR e.eventDate <= :rangeEnd)")
    List<Event> findAdminEvents(@Param("users") List<Long> users,
                                @Param("states") List<EventState> states,
                                @Param("categories") List<Long> categories,
                                @Param("rangeStart") LocalDateTime rangeStart,
                                @Param("rangeEnd") LocalDateTime rangeEnd,
                                Pageable pageable);

    // Для публичного поиска: только опубликованные
    @Query("SELECT e FROM Event e WHERE e.state = 'PUBLISHED' " +
            "AND (:text IS NULL OR LOWER(e.annotation) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%')) " +
            "OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:text AS string), '%'))) " +
            "AND (coalesce(:categories, null) IS NULL OR e.category.id IN :categories) " +
            "AND (coalesce(:paid, null) IS NULL OR e.paid = :paid) " +
            "AND (coalesce(:rangeStart, null) IS NULL OR e.eventDate >= :rangeStart) " +
            "AND (coalesce(:rangeEnd, null) IS NULL OR e.eventDate <= :rangeEnd)")
    List<Event> findPublicEvents(@Param("text") String text,
                                 @Param("categories") List<Long> categories,
                                 @Param("paid") Boolean paid,
                                 @Param("rangeStart") LocalDateTime rangeStart,
                                 @Param("rangeEnd") LocalDateTime rangeEnd,
                                 Pageable pageable);

    List<Event> findAllByInitiatorId(Long userId, Pageable pageable);
}
