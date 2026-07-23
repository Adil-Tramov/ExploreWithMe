package stats.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import stats.service.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {
    Optional<EventSimilarity> findByEvent1AndEvent2(Long event1, Long event2);

    @Query("SELECT e FROM EventSimilarity e " +
            "WHERE e.event1 IN :eventIds OR e.event2 IN :eventIds " +
            "ORDER BY e.similarity DESC")
    List<EventSimilarity> findByEvent1InOrEvent2InOrderBySimilarityDesc(
            @Param("eventIds") List<Long> eventIds
    );

    @Query("SELECT e FROM EventSimilarity e " +
            "WHERE e.event1 = :eventId OR e.event2 = :eventId " +
            "ORDER BY e.similarity DESC")
    List<EventSimilarity> findByEvent1OrEvent2OrderBySimilarityDesc(
            @Param("eventId") Long eventId
    );
}
