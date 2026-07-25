package stats.service.mapper;

import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import stats.service.model.UserAction;

public class UserActionMapper {

    public static UserAction toEntity(UserActionAvro avro) {
        return UserAction.builder()
                .userId((long) avro.getUserId())
                .eventId((long) avro.getEventId())
                .rating(toRating(avro.getActionType()))
                .ts(avro.getTimestamp())
                .build();
    }

    private static Float toRating(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> 0.4F;
            case REGISTER -> 0.8F;
            case LIKE -> 1.0F;
        };
    }
}
