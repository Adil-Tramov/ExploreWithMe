package stats.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import stats.service.model.UserAction;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    Optional<UserAction> findByUserIdAndEventId(Long userId, Long eventId);

    List<UserAction> findAllByUserIdOrderByTsDesc(Long userId);

    List<UserAction> findAllByUserId(Long userId);

    @Query("SELECT ua FROM UserAction ua WHERE ua.userId IN :userIds AND ua.eventId IN :eventIds")
    List<UserAction> findAllByUserIdInAndEventIdIn(@Param("userIds") List<Long> userIds,
                                                   @Param("eventIds") List<Long> eventIds);

    @Query("SELECT ua FROM UserAction ua WHERE ua.eventId IN :eventIds")
    List<UserAction> findAllByEventIdIn(@Param("eventIds") List<Long> eventIds);
}