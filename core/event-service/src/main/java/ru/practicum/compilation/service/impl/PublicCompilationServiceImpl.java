package ru.practicum.compilation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.compilation.service.PublicCompilationService;
import ru.practicum.exception.NotFoundException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicCompilationServiceImpl implements PublicCompilationService {

    private final CompilationRepository compilationRepository;

    @Override
    public List<Compilation> getCompilations(Boolean pinned, int from, int size) {
        log.info("Получение подборок с параметрами: pinned={}, from={}, size={}", pinned, from, size);

        Pageable pageable = PageRequest.of(from / size, size);

        if (pinned != null) {
            return compilationRepository.findAllByPinned(pinned, pageable);
        }

        return compilationRepository.findAll(pageable).getContent();
    }

    @Override
    public Compilation getCompilationById(Long compId) {
        log.info("Получение подборки по id: {}", compId);

        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
    }
}