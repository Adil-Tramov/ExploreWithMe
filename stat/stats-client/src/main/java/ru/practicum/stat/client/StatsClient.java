package ru.practicum.stat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class StatsClient {
    private final RestClient restClient;
    private final LoadBalancerClient loadBalancerClient;
    private final String serviceId;

    public StatsClient(@Value("${stats.service.name:stats-server}") String serviceId,
                       LoadBalancerClient loadBalancerClient) {
        this.serviceId = serviceId;
        this.loadBalancerClient = loadBalancerClient;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    public EndpointHitDto hit(EndpointHitDto hit) {
        String baseUrl = getServiceUrl();
        return restClient.post()
                .uri(baseUrl + "/hit")
                .body(hit)
                .retrieve()
                .body(EndpointHitDto.class);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {

        String baseUrl = getServiceUrl();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/stats")
                .queryParam("start", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("end", end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }

        String uriString = builder.build().toUriString();

        ViewStatsDto[] response = restClient.get()
                .uri(uriString)
                .retrieve()
                .body(ViewStatsDto[].class);

        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }

    private String getServiceUrl() {
        ServiceInstance instance = loadBalancerClient.choose(serviceId);
        if (instance == null) {
            throw new IllegalStateException("Нет доступных экземпляров сервиса " + serviceId);
        }
        return instance.getUri().toString();
    }
}