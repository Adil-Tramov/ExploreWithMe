package ru.practicum.main.service.rating.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.service.rating.model.EventRating;

import java.util.List;
import java.util.Optional;


public interface EventRatingRepository extends JpaRepository<EventRating, Long> {

    Optional<EventRating> findByEventIdAndUserId(Long eventId, Long userId);

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    @Query("SELECT r FROM EventRating r WHERE r.userId = :userId ORDER BY r.created DESC")
    List<EventRating> findByUserIdOrderByCreatedDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM EventRating r WHERE r.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM EventRating r WHERE r.userId = :userId AND r.ratingType = :type ORDER BY r.created DESC")
    List<EventRating> findByUserIdAndRatingTypeOrderByCreatedDesc(
            @Param("userId") Long userId,
            @Param("type") EventRating.RatingType type,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM EventRating r WHERE r.userId = :userId AND r.ratingType = :type")
    long countByUserIdAndRatingType(@Param("userId") Long userId, @Param("type") EventRating.RatingType type);

    @Query("SELECT r.eventId, " +
            "COUNT(CASE WHEN r.ratingType = 'LIKE' THEN 1 END), " +
            "COUNT(CASE WHEN r.ratingType = 'DISLIKE' THEN 1 END) " +
            "FROM EventRating r " +
            "WHERE r.eventId = :eventId " +
            "GROUP BY r.eventId")
    List<Object[]> getRatingStatsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT r.eventId, " +
            "(COUNT(CASE WHEN r.ratingType = 'LIKE' THEN 1 END) - " +
            "COUNT(CASE WHEN r.ratingType = 'DISLIKE' THEN 1 END)) as rating " +
            "FROM EventRating r " +
            "GROUP BY r.eventId ")
    List<Object[]> findTopRatedEvents(Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM EventRating r WHERE r.eventId = :eventId AND r.userId = :userId")
    void deleteByEventIdAndUserId(@Param("eventId") Long eventId, @Param("userId") Long userId);
}