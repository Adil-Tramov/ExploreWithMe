package ru.practicum.main.service.compilation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.service.compilation.dto.CompilationDto;
import ru.practicum.main.service.compilation.dto.NewCompilationDto;
import ru.practicum.main.service.compilation.dto.UpdateCompilationRequest;
import ru.practicum.main.service.compilation.mapper.CompilationMapper;
import ru.practicum.main.service.compilation.model.Compilation;
import ru.practicum.main.service.compilation.repository.CompilationRepository;
import ru.practicum.main.service.compilation.service.CompilationService;
import ru.practicum.main.service.event.dto.EventShortDto;
import ru.practicum.main.service.event.mapper.EventMapper;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.repository.EventRepository;
import ru.practicum.main.service.exception.NotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        log.info("Создание подборки: {}", newCompilationDto);

        List<Event> events = getEventsByIds(newCompilationDto.getEvents());

        Compilation compilation = CompilationMapper.toEntity(newCompilationDto, events);
        compilation = compilationRepository.save(compilation);

        log.info("Подборка создана с id: {}", compilation.getId());
        return toDtoWithEvents(compilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Удаление подборки с id: {}", compId);
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Подборка с id=" + compId + " не найдена");
        }
        compilationRepository.deleteById(compId);
        log.info("Подборка {} удалена", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest request) {
        log.info("Обновление подборки {} данными: {}", compId, request);

        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        List<Event> events = null;
        if (request.getEvents() != null) {
            events = getEventsByIds(request.getEvents());
        }

        CompilationMapper.updateEntity(compilation, request, events);
        compilation = compilationRepository.save(compilation);

        log.info("Подборка {} обновлена", compId);
        return toDtoWithEvents(compilation);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        log.info("Получение подборок: pinned={}, from={}, size={}", pinned, from, size);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "id"));
        List<Compilation> compilations;

        if (pinned == null) {
            compilations = compilationRepository.findAll(pageable).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, pageable);
        }

        return compilations.stream()
                .map(this::toDtoWithEvents)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("Получение подборки по id: {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
        return toDtoWithEvents(compilation);
    }

    // Вспомогательные методы

    private List<Event> getEventsByIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Event> events = eventRepository.findAllById(eventIds);
        if (events.size() != eventIds.size()) {
            // Найдём, какие id отсутствуют
            List<Long> foundIds = events.stream().map(Event::getId).toList();
            List<Long> missing = eventIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new NotFoundException("События с id " + missing + " не найдены");
        }
        return events;
    }

    private CompilationDto toDtoWithEvents(Compilation compilation) {
        List<EventShortDto> eventShortDtos = compilation.getEvents().stream()
                .map(EventMapper::toShortDto)
                .collect(Collectors.toList());
        return CompilationMapper.toDto(compilation, eventShortDtos);
    }
}