package ru.practicum.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
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
public class CollectorController extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final Producer<String, SpecificRecordBase> producer;
    private final KafkaTopicsProperties kafkaTopics;

    public CollectorController(KafkaProducerConfig kafkaProducerConfig, KafkaTopicsProperties kafkaTopics) {
        this.producer = kafkaProducerConfig.createProducer();
        this.kafkaTopics = kafkaTopics;
        log.info("Инициализирован CollectorController с топиком: {}", kafkaTopics.getOutputTopic());
    }

    @Override
    public void collectUserAction(UserActionProto proto, StreamObserver<Empty> responseObserver) {
        log.info("----------------------------");
        log.info("Получены данные в proto: {}", proto);

        UserActionAvro avro = UserActionMapper.toAvro(proto);
        log.info("Маппинг данных в avro: {}", avro);

        try {
            String outputTopic = kafkaTopics.getOutputTopic();
            producer.send(new ProducerRecord<>(outputTopic, avro));
            log.info("Отправлено сообщение в топик: {}", outputTopic);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения в Kafka", e);
            responseObserver.onError(new StatusRuntimeException(Status.fromThrowable(e)));
        }
    }

    @PreDestroy
    public void close() {
        try {
            producer.flush();
            producer.close(Duration.ofSeconds(10));
            log.info("Producer закрыт успешно");
        } catch (Exception e) {
            log.error("Ошибка при закрытии producer", e);
        }
    }
}