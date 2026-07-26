package ru.practicum.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import ru.practicum.client.KafkaProducerConfig;
import ru.practicum.config.KafkaTopicsProperties;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.mapper.UserActionMapper;
import stats.service.collector.UserActionControllerGrpc;
import stats.service.collector.UserActionProto;

import java.time.Duration;

@Slf4j
@GrpcService
public class CollectorController extends UserActionControllerGrpc.UserActionControllerImplBase implements AutoCloseable {

    private final Producer<String, SpecificRecordBase> producer;
    private final KafkaTopicsProperties topicsProperties;

    public CollectorController(KafkaProducerConfig kafkaProducerConfig, KafkaTopicsProperties topicsProperties) {
        this.producer = kafkaProducerConfig.createProducer();
        this.topicsProperties = topicsProperties;
        log.info("CollectorController инициализирован. Топик: {}", topicsProperties.getUserActions());
    }

    @Override
    public void collectUserAction(UserActionProto proto, StreamObserver<Empty> responseObserver) {
        log.info("----------------------------");
        log.info("Получены данные в proto: {}", proto);
        UserActionAvro avro = UserActionMapper.toAvro(proto);
        log.info("Маппинг данных в avro: {}", avro);
        try {
            String topic = topicsProperties.getUserActions();
            producer.send(new ProducerRecord<>(topic, avro));
            log.info("Сообщение отправлено в топик: {}", topic);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения в Kafka", e);
            responseObserver.onError(new StatusRuntimeException(Status.fromThrowable(e)));
        }
    }

    @Override
    public void close() {
        try {
            producer.flush();
            producer.close(Duration.ofSeconds(10));
            log.info("Продюсер закрыт");
        } catch (Exception e) {
            log.error("Ошибка при закрытии продюсера", e);
        }
    }
}