package ru.practicum.faign;

import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.dto.eventDto.EventFullDto;


@FeignClient(name = "event-service", configuration = FeignConfig.class)
public interface EventServiceFeign {

    @GetMapping("/events/{id}/WithoutHttp")
    EventFullDto getEventByIdWithoutHttp(@PathVariable @Positive Long id);
}
