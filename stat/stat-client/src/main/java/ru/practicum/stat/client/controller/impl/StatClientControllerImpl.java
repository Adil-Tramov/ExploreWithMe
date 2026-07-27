package ru.practicum.stat.client.controller.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.statServerDto.RecommendedEventDto;
import ru.practicum.controllerInterface.StatClientController;
import ru.practicum.stat.client.mapper.RecommendedEventMapper;
import stats.service.collector.ActionTypeProto;
import stats.service.collector.UserActionControllerGrpc;
import stats.service.collector.UserActionProto;
import stats.service.dashboard.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
@Validated
@Slf4j
@Component
public class StatClientControllerImpl implements StatClientController {

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub userActionControl;

    public void saveStat(Long userId, Long eventId, ActionTypeProto action) {
        UserActionProto userAction = UserActionProto.newBuilder()
                .setUserId(Math.toIntExact(userId))
                .setEventId(Math.toIntExact(eventId))
                .setActionType(action)
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .build();
        userActionControl.collectUserAction(userAction);
    }

    public List<RecommendedEventDto> getRecommendationsForUserAsList(@RequestParam long userId,
                                                                       @RequestParam int maxResults) {
        log.info("getRecommendationsForUser, userId={}, maxResults={}", userId, maxResults);
        return getRecommendationsForUser(userId, maxResults)
                .map(RecommendedEventMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<RecommendedEventDto> getSimilarEventsAsList(@RequestParam long eventId,
                                                              @RequestParam long userId,
                                                              @RequestParam int maxResults) {
        log.info("getSimilarEvents, eventId={}, userId={}, maxResults={}", eventId, userId, maxResults);
        return getSimilarEvents(eventId, userId, maxResults)
                .map(RecommendedEventMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<RecommendedEventDto> getInteractionsCountAsList(@RequestBody List<Long> eventIds) {
        log.info("getInteractionsCount, eventIds={}", eventIds);
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecommendedEventProto> resultProtoList = getInteractionsCount(eventIds).toList();
        log.info("resultProtoList: {}", resultProtoList);
        List<RecommendedEventDto> resultDtoList = resultProtoList.stream()
                .map(RecommendedEventMapper::toDto)
                .toList();
        log.info("resultDtoList: {}", resultDtoList);
        return resultDtoList;
    }

    public Stream<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(Math.toIntExact(userId))
                .setMaxResults(maxResults)
                .build();

        Iterator<RecommendedEventProto> iterator = client.getRecommendationsForUser(request);
        return asStream(iterator);
    }

    public Stream<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                .setUserId(Math.toIntExact(userId))
                .setEventId(Math.toIntExact(eventId))
                .setMaxResults(maxResults)
                .build();
        Iterator<RecommendedEventProto> iterator = client.getSimilarEvents(request);

        return asStream(iterator);
    }

    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        log.info("grpc call: getInteractionsCount, eventIds={}", eventIds);
        if (eventIds == null || eventIds.isEmpty()) {
            return Stream.empty();
        }

        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds.stream()
                        .map(Long::intValue)
                        .collect(Collectors.toList()))
                .build();
        log.info("request {}", request);
        Iterator<RecommendedEventProto> iterator = client.getInteractionsCount(request);
        log.info("grpc iterator: {}", iterator);
        return asStream(iterator);
    }

    private Stream<RecommendedEventProto> asStream(Iterator<RecommendedEventProto> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
        );
    }
}