package stats.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import stats.service.service.EventSimilarityProcessor;
import stats.service.service.UserActionProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class Analyzer {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Analyzer.class, args);

        final EventSimilarityProcessor eventSimilarityProcessor = context.getBean(EventSimilarityProcessor.class);
        final UserActionProcessor userActionProcessor = context.getBean(UserActionProcessor.class);

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        executorService.submit(eventSimilarityProcessor::start);
        executorService.submit(userActionProcessor::start);
    }
}
