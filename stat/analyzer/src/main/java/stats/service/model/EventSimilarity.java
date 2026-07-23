package stats.service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "similarities")

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSimilarity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event1", nullable = false)
    private Long event1;
    @Column(name = "event2", nullable = false)
    private Long event2;
    @Column(name = "similarity", nullable = false)
    private Float similarity;
    @Column(name = "ts", nullable = false)
    private Instant ts;
}
