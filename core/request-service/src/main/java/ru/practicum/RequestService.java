package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import ru.practicum.exception.ErrorHandler;


@SpringBootApplication
@ComponentScan(basePackages = {"ru.practicum"})
@Import({ErrorHandler.class})
public class RequestService {
    public static void main(String[] args) {
        SpringApplication.run(RequestService.class, args);
    }
}