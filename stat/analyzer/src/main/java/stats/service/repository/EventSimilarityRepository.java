package stats.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import stats.service.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {

    List<EventSimilarity> findByEvent1OrEvent2OrderBySimilarityDesc(Long eventId);

    List<EventSimilarity> findByEvent1InOrEvent2InOrderBySimilarityDesc(List<Long> eventIds);

    @Query("SELECT e FROM EventSimilarity e WHERE e.event1 IN :eventIds OR e.event2 IN :eventIds")
    List<EventSimilarity> findByEvent1InOrEvent2In(@Param("eventIds") List<Long> eventIds);

    @Query("SELECT e FROM EventSimilarity e WHERE e.event1 = :event1 AND e.event2 = :event2")
    Optional<EventSimilarity> findByEvent1AndEvent2(@Param("event1") Long event1,
                                                    @Param("event2") Long event2);
}