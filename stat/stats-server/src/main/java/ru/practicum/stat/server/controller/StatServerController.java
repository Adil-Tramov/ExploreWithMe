package ru.practicum.stat.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;
import ru.practicum.stat.server.service.StatServerService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@Slf4j
public class StatServerController {
    private final StatServerService statServerService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointHitDto saveHit(@RequestBody EndpointHitDto dto) {
        log.debug("PostMapping /hit. dto: {}", dto);
        return statServerService.saveHit(dto);
    }

    @GetMapping("/stats")
    @ResponseStatus(HttpStatus.OK)
    public List<ViewStatsDto> getHits(@RequestParam("start")
                                      @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                                      LocalDateTime start,
                                      @RequestParam("end")
                                      @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                                      LocalDateTime end,
                                      @RequestParam(name = "uris", required = false)
                                      List<String> uris,
                                      @RequestParam(name = "unique", defaultValue = "false")
                                      boolean unique) {
        log.debug("GetMapping /stats. Params: start: {}, end: {}, uris: {}, unique {}", start, end, uris, unique);
        return statServerService.getStats(start, end, uris, unique);
    }
}
