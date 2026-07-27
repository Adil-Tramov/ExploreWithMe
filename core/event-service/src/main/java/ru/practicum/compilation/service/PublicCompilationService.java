package ru.practicum.compilation.service;

import ru.practicum.compilation.model.Compilation;

import java.util.List;

public interface PublicCompilationService {

    List<Compilation> getCompilations(Boolean pinned, int from, int size);

    Compilation getCompilationById(Long compId);
}