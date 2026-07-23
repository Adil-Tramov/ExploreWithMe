package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.practicum.exception.ErrorHandler;
import ru.practicum.stat.client.StatsClient;

@SpringBootApplication
@Import({StatsClient.class, ErrorHandler.class})
public class EventService {
    public static void main(String[] args) {
        SpringApplication.run(EventService.class, args);
    }
}
