package ru.practicum.compilation.service;

import ru.practicum.compilation.model.Compilation;
import ru.practicum.dto.compilationDto.NewCompilationDto;
import ru.practicum.dto.compilationDto.UpdateCompilationRequest;

public interface AdminCompilationService {

    Compilation createCompilation(NewCompilationDto newCompilationDto);

    void deleteCompilation(Long compId);

    Compilation updateCompilation(Long compId, UpdateCompilationRequest request);
}