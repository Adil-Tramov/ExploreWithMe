package ru.practicum.compilation.service;

import ru.practicum.compilation.model.Compilation;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.event.model.Event;

import java.util.List;
import java.util.Map;

public interface AdminCompilationService {

    Compilation createCompilation(NewCompilationDto newCompilationDto);

    void deleteCompilation(Long compId);

    Compilation updateCompilation(Long compId, UpdateCompilationRequest request);

    Map<Long, Long> getViewsForEvents(List<Event> events);
}