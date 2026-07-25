package stats.service.dashboard;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import stats.service.service.RecommendationService;

import java.util.List;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final RecommendationService recommendationService;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        log.info("Получен запрос на получение рекомендаций для пользователя ID: {}", request.getUserId());
        try {
            Long userId = (long) request.getUserId();
            int maxResults = request.getMaxResults();

            List<RecommendedEventProto> recommendations = recommendationService
                    .getRecommendationsForUser(userId, maxResults);

            for (RecommendedEventProto recommendation : recommendations) {
                responseObserver.onNext(recommendation);
            }
            log.info("Успешно отправлено {} рекомендаций для пользователя ID: {}", recommendations.size(), userId);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при формировании рекомендаций для пользователя ID: {}", request.getUserId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        log.info("Получен запрос на поиск похожих событий: userId={}, eventId={}",
                request.getUserId(), request.getEventId());
        try {
            Long userId = (long) request.getUserId();
            Long eventId = (long) request.getEventId();
            int maxResults = request.getMaxResults();

            List<RecommendedEventProto> similarEvents = recommendationService
                    .getSimilarEvents(userId, eventId, maxResults);

            for (RecommendedEventProto event : similarEvents) {
                responseObserver.onNext(event);
            }
            log.info("Успешно отправлены похожие события для пользователя ID: {}", userId);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при поиске похожих событий для eventId: {}", request.getEventId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Integer> eventIdList = request.getEventIdList();
        log.info("Получен запрос на получение количества взаимодействий для {} событий: {}",
                eventIdList.size(), eventIdList);

        try {
            if (eventIdList.isEmpty()) {
                log.info("Список eventId пуст, возвращаем пустой ответ");
                responseObserver.onCompleted();
                return;
            }

            List<RecommendedEventProto> interactionsCount = recommendationService
                    .getInteractionsCount(eventIdList);

            for (RecommendedEventProto event : interactionsCount) {
                responseObserver.onNext(event);
            }
            log.info("Успешно отправлены оценки для {} событий", interactionsCount.size());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при расчете количества взаимодействий для событий: {}",
                    request.getEventIdList(), e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Ошибка при расчете взаимодействий: " + e.getMessage())
                            .asRuntimeException());
        }
    }
}