package ru.practicum.controllerInterface;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.statServerDto.RecommendedEventDto;
import stats.service.collector.ActionTypeProto;

import java.util.List;

public interface StatClientController {


    void saveStat(Long userId, Long eventId, ActionTypeProto action);

    List<RecommendedEventDto> getRecommendationsForUserAsList(@RequestParam long userId,
                                                              @RequestParam int maxResults);

    List<RecommendedEventDto> getSimilarEventsAsList(@RequestParam long eventId,
                                                     @RequestParam long userId,
                                                     @RequestParam int maxResults);

    List<RecommendedEventDto> getInteractionsCountAsList(@RequestBody List<Long> eventIds);

}
