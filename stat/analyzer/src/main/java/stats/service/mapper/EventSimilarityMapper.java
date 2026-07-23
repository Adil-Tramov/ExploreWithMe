package stats.service.mapper;

import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import stats.service.model.EventSimilarity;

public class EventSimilarityMapper {

    public static EventSimilarity toEntity(EventSimilarityAvro avro) {
        return EventSimilarity.builder()
                .event1((long) avro.getEventA())
                .event2((long) avro.getEventB())
                .similarity((float) avro.getScore())
                .ts(avro.getTimestamp())
                .build();
    }
}
