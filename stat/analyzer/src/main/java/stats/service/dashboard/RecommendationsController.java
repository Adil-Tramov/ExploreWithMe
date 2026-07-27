package stats.service.dashboard;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final RecommendationsService recommendationsService;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        log.info("Получен запрос на получение рекомендаций для пользователя ID: {}", request.getUserId());
        try {
            Long userId = (long) request.getUserId();
            int maxResults = request.getMaxResults();

            List<RecommendedEventProto> recommendations =
                    recommendationsService.getRecommendationsForUser(userId, maxResults);

            recommendations.forEach(responseObserver::onNext);
            log.info("Успешно отправлено {} рекомендаций для пользователя ID: {}", recommendations.size(), userId);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при формировании рекомендаций для пользователя ID: {}", request.getUserId(), e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Ошибка при формировании рекомендаций: " + e.getMessage())
                            .asRuntimeException()
            );
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

            List<RecommendedEventProto> similarEvents =
                    recommendationsService.getSimilarEvents(userId, eventId, maxResults);

            similarEvents.forEach(responseObserver::onNext);
            log.info("Успешно отправлены похожие события для пользователя ID: {}", userId);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при поиске похожих событий для eventId: {}", request.getEventId(), e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Ошибка при поиске похожих событий: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Integer> eventIdList = request.getEventIdList();
        log.info("Получен запрос на получение количества взаимодействий для {} событий", eventIdList.size());

        try {
            List<RecommendedEventProto> interactions =
                    recommendationsService.getInteractionsCount(eventIdList);

            interactions.forEach(responseObserver::onNext);
            log.info("Успешно отправлены оценки для {} событий", interactions.size());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка при расчете количества взаимодействий", e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Ошибка при расчете взаимодействий: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }
}