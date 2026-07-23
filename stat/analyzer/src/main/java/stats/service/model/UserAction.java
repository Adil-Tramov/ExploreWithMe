package stats.service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "interactions")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "rating", nullable = false)
    private Float rating;
    @Column(name = "ts", nullable = false)
    private Instant ts;
}
