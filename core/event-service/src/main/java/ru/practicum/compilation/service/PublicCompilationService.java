package ru.practicum.compilation.service;

import ru.practicum.compilation.model.Compilation;
import ru.practicum.event.model.Event;

import java.util.List;
import java.util.Map;

public interface PublicCompilationService {

    List<Compilation> getCompilations(Boolean pinned, int from, int size);

    Compilation getCompilationById(Long compId);

    Map<Long, Long> getViewsForEvents(List<Event> events);
}