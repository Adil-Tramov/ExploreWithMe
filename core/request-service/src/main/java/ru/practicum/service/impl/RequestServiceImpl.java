package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.requestDto.EventRequestStatusUpdateResult;
import ru.practicum.dto.requestDto.RequestStatus;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.repository.RequestRepository;
import ru.practicum.service.RequestService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;

    @Override
    public List<ParticipationRequest> getUserRequests(Long userId) {
        log.info("Получение заявок пользователя с id: {}", userId);
        return requestRepository.findAllByRequesterId(userId);
    }

    @Override
    @Transactional
    public ParticipationRequest createRequest(Long userId, Long eventId, EventFullDto event, UserDto requester) {
        log.info("Создание заявки от пользователя {} на событие {}", userId, eventId);

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос " +
                    "на участие в своём событии");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        requestRepository.findByEventIdAndRequesterId(eventId, userId)
                .ifPresent(r -> {
                    throw new ConflictException("Нельзя добавить повторный запрос на это событие");
                });

        if (event.getParticipantLimit() > 0) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmedCount >= event.getParticipantLimit()) {
                throw new ConflictException("Достигнут лимит участников для события");
            }
        }

        ParticipationRequest request = RequestMapper.toNewRequest(event, userId);

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        }

        request = requestRepository.save(request);
        log.info("Заявка создана с id: {}", request.getId());

        return request;
    }

    @Override
    @Transactional
    public ParticipationRequest cancelRequest(Long userId, Long requestId) {
        log.info("Отмена заявки {} пользователем {}", requestId, userId);

        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + requestId +
                        " не найден или не принадлежит пользователю"));

        request.setStatus(RequestStatus.CANCELED);
        request = requestRepository.save(request);
        log.info("Заявка {} отменена", requestId);
        return request;
    }

    @Override
    public List<ParticipationRequest> getEventRequests(Long userId, Long eventId, EventFullDto event) {
        log.info("Получение заявок на событие {} для пользователя {}", eventId, userId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }
        return requestRepository.findAllByEventId(eventId);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateEventRequestsStatus(Long userId, Long eventId,
                                                                    EventRequestStatusUpdateRequest updateRequest,
                                                                    EventFullDto event) {
        log.info("updateEventRequestsStatus: userId={}, eventId={}, requestIds={}", userId, eventId, updateRequest.getRequestIds());

        if (!event.getInitiator().getId().equals(userId)) {
            log.warn("Initiator mismatch: event initiator id = {}, userId = {}", event.getInitiator().getId(), userId);
            throw new ConditionsNotMetException("Пользователь не является инициатором события");
        }

        List<Long> requestIds = updateRequest.getRequestIds();
        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(requestIds);

        for (ParticipationRequest req : requests) {
            if (!req.getEventId().equals(eventId)) {
                throw new ConditionsNotMetException("Запрос с id=" + req.getId() + " не относится к событию " + eventId);
            }
        }

        RequestStatus newStatus = updateRequest.getStatus();
        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            long limit = event.getParticipantLimit();

            for (ParticipationRequest req : requests) {
                if (req.getStatus() != RequestStatus.PENDING) {
                    throw new ConditionsNotMetException("Нельзя подтвердить запрос, который не в статусе PENDING");
                }
                if (limit == 0 || confirmedCount < limit) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(req);
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(req);
                    throw new ConflictException("Лимит участников достигнут");
                }
            }
        } else if (newStatus == RequestStatus.REJECTED) {
            for (ParticipationRequest req : requests) {
                if (req.getStatus() == RequestStatus.CONFIRMED) {
                    throw new ConflictException("Нельзя отменить принятую заявку");
                }
                if (req.getStatus() != RequestStatus.REJECTED) {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(req);
                } else {
                    rejected.add(req);
                }
            }
        } else {
            throw new IllegalArgumentException("Недопустимый статус: " + newStatus);
        }

        requestRepository.saveAll(requests);
        log.info("Статусы заявок обновлены");

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed.stream().map(RequestMapper::toDto).collect(Collectors.toList()))
                .rejectedRequests(rejected.stream().map(RequestMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    @Override
    public boolean confirmUserRegisterOnEvent(Long userId, Long eventId, RequestStatus requestStatus) {
        log.info("Проверяем с параметрами userId: {}, eventId: {}, status: {}", userId, eventId, requestStatus);
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(eventId, userId, requestStatus);
    }

    @Override
    public List<ParticipationRequest> getAllByEventIdInAndStatus(Long userId, List<Long> eventId, RequestStatus requestStatus) {
        return requestRepository.findAllByEventIdInAndStatus(eventId, requestStatus);
    }
}