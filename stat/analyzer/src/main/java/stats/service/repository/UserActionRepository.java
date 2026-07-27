package stats.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import stats.service.model.UserAction;

import java.util.List;
import java.util.Optional;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {
    Optional<UserAction> findByUserIdAndEventId(Long userId, Long eventId);

    List<UserAction> findAllByEventIdIn(List<Long> eventId);

    List<UserAction> findAllByUserId(Long userId);

    @Query("SELECT ua FROM UserAction ua " +
            "WHERE ua.userId = :userId " +
            "ORDER BY ua.ts DESC")
    List<UserAction> findAllByUserIdOrderByTsDesc(@Param("userId") Long userId);
}

