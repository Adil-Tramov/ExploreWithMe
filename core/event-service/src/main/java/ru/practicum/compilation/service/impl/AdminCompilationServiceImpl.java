package ru.practicum.compilation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.compilation.service.AdminCompilationService;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.NotFoundException;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminCompilationServiceImpl implements AdminCompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public Compilation createCompilation(NewCompilationDto newCompilationDto) {
        log.info("Создание подборки: {}", newCompilationDto);

        List<Event> events = getEventsByIds(newCompilationDto.getEvents());

        Compilation compilation = CompilationMapper.toEntity(newCompilationDto, events);
        compilation = compilationRepository.save(compilation);

        log.info("Подборка создана с id: {}", compilation.getId());
        return compilation;
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Удаление подборки с id: {}", compId);

        findCompilationById(compId);
        compilationRepository.deleteById(compId);

        log.info("Подборка {} удалена", compId);
    }

    @Override
    @Transactional
    public Compilation updateCompilation(Long compId, UpdateCompilationRequest request) {
        log.info("Обновление подборки {} данными: {}", compId, request);

        Compilation compilation = findCompilationById(compId);

        List<Event> events = null;
        if (request.getEvents() != null) {
            events = getEventsByIds(request.getEvents());
        }

        CompilationMapper.updateEntity(compilation, request, events);
        compilation = compilationRepository.save(compilation);

        log.info("Подборка {} обновлена", compId);
        return compilation;
    }

    private Compilation findCompilationById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
    }

    private List<Event> getEventsByIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventRepository.findAllById(eventIds);

        validateAllEventsFound(eventIds, events);

        return events;
    }

    private void validateAllEventsFound(List<Long> requestedIds, List<Event> foundEvents) {
        if (foundEvents.size() != requestedIds.size()) {
            List<Long> foundIds = foundEvents.stream().map(Event::getId).toList();
            List<Long> missing = requestedIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new NotFoundException("События с id " + missing + " не найдены");
        }
    }
}