package ru.practicum.main.service.event.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.service.category.model.Category;
import ru.practicum.main.service.category.repository.CategoryRepository;
import ru.practicum.main.service.event.dto.*;
import ru.practicum.main.service.event.mapper.EventMapper;
import ru.practicum.main.service.event.model.Event;
import ru.practicum.main.service.event.model.EventState;
import ru.practicum.main.service.event.repository.EventRepository;
import ru.practicum.main.service.event.service.EventService;
import ru.practicum.main.service.exception.ConditionsNotMetException;
import ru.practicum.main.service.exception.ConflictException;
import ru.practicum.main.service.exception.NotFoundException;
import ru.practicum.main.service.request.model.RequestStatus;
import ru.practicum.main.service.request.repository.RequestRepository;
import ru.practicum.main.service.user.model.User;
import ru.practicum.main.service.user.repository.UserRepository;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final StatsClient statsClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        log.info("Создание события пользователем {}", userId);

        User user = userRepository.findById(Math.toIntExact(userId))
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));

        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConditionsNotMetException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }

        Event event = EventMapper.toEvent(dto, category, user);
        event = eventRepository.save(event);

        log.info("Событие создано с id: {}", event.getId());
        return EventMapper.toFullDto(event, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        log.info("Получение событий пользователя {}", userId);
        checkUserExists(userId);

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);

        return enrichEventsWithStats(events, false);
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("Получение события {} пользователя {}", eventId, userId);
        Event event = findEventByIdAndInitiator(eventId, userId);
        Long confirmedRequests = getConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, confirmedRequests, views);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        log.info("Обновление события {} пользователем {}", eventId, userId);

        Event event = findEventByIdAndInitiator(eventId, userId);

        if (event.getState() != EventState.CANCELED && event.getState() != EventState.PENDING) {
            throw new ConflictException("Изменить можно только отмененные события или события в состоянии ожидания модерации");
        }

        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConditionsNotMetException("Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        }

        EventMapper.updateEventFromUserRequest(event, dto, category);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "SEND_TO_REVIEW":
                    event.setState(EventState.PENDING);
                    break;
                case "CANCEL_REVIEW":
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new IllegalArgumentException("Некорректное действие: " + dto.getStateAction());
            }
        }

        event = eventRepository.save(event);
        log.info("Событие {} обновлено", eventId);

        Long confirmedRequests = getConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, confirmedRequests, views);
    }

    @Override
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, int from, int size,
                                               HttpServletRequest request) {
        log.info("Публичный поиск событий");

        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        if (rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }

        Sort sortBy;
        if (sort != null && sort.equals("VIEWS")) {
            sortBy = Sort.by(Sort.Direction.DESC, "id");
        } else {
            sortBy = Sort.by(Sort.Direction.ASC, "eventDate");
        }

        Pageable pageable = PageRequest.of(from / size, size, sortBy);
        List<Event> events = eventRepository.findPublicEvents(text, categories, paid, rangeStart, rangeEnd, pageable);

        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = events.stream()
                    .filter(e -> {
                        if (e.getParticipantLimit() == 0) return true;
                        long confirmed = requestRepository.countByEventIdAndStatus(e.getId().intValue(), RequestStatus.CONFIRMED);
                        return confirmed < e.getParticipantLimit();
                    })
                    .collect(Collectors.toList());
        }

        List<EventShortDto> result = enrichEventsWithStats(events, true);

        if (sort != null && sort.equals("VIEWS")) {
            result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
        }

        saveHit(request);
        return result;
    }

    @Override
    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) {
        log.info("Публичное получение события {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        saveHit(request);

        Long confirmedRequests = getConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());

        return EventMapper.toFullDto(event, confirmedRequests, views);
    }

    @Override
    public List<EventFullDto> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        log.info("Админский поиск событий");

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAdminEvents(users, states, categories, rangeStart, rangeEnd, pageable);

        return enrichEventsFullWithStats(events);
    }

    @Override
    @Transactional
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest dto) {
        log.info("Админское обновление события {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        }

        EventMapper.updateEventFromAdminRequest(event, dto, category);

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "PUBLISH_EVENT":
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Опубликовать можно только событие в состоянии ожидания");
                    }
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new ConditionsNotMetException("Дата начала события должна быть не ранее чем через час от публикации");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case "REJECT_EVENT":
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Нельзя отклонить уже опубликованное событие");
                    }
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new IllegalArgumentException("Некорректное действие: " + dto.getStateAction());
            }
        }

        event = eventRepository.save(event);
        log.info("Событие {} обновлено администратором", eventId);

        Long confirmedRequests = getConfirmedRequests(eventId);
        Long views = getViewsForEvent(eventId, event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn());
        return EventMapper.toFullDto(event, confirmedRequests, views);
    }

    // Вспомогательные методы

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(Math.toIntExact(userId))) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }

    private Event findEventByIdAndInitiator(Long eventId, Long userId) {
        return eventRepository.findById(eventId)
                .filter(event -> event.getInitiator().getId().equals(Math.toIntExact(userId)))
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено или не принадлежит пользователю"));
    }

    private Long getConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId.intValue(), RequestStatus.CONFIRMED);
    }

    private Long getViewsForEvent(Long eventId, LocalDateTime start) {
        if (start == null) start = LocalDateTime.now().minusYears(10);
        List<String> uris = List.of("/events/" + eventId);
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);
        if (!stats.isEmpty()) {
            return stats.getFirst().getHits();
        }
        return 0L;
    }

    private void saveHit(HttpServletRequest request) {
        EndpointHitDto hit = EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();
        statsClient.hit(hit);
    }

    private List<EventShortDto> enrichEventsWithStats(List<Event> events, boolean onlyPublished) {
        if (events.isEmpty()) return List.of();

        Map<Long, Long> confirmedMap = events.stream()
                .collect(Collectors.toMap(
                        Event::getId,
                        e -> requestRepository.countByEventIdAndStatus(e.getId().intValue(), RequestStatus.CONFIRMED)
                ));

        LocalDateTime earliestStart = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        List<ViewStatsDto> stats = statsClient.getStats(earliestStart, LocalDateTime.now(), uris, false);
        Map<Long, Long> viewsMap = stats.stream()
                .collect(Collectors.toMap(
                        v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                        ViewStatsDto::getHits,
                        (a, b) -> a
                ));

        return events.stream()
                .map(e -> EventMapper.toShortDto(
                        e,
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<EventFullDto> enrichEventsFullWithStats(List<Event> events) {
        if (events.isEmpty()) return List.of();

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        Map<Long, Long> confirmedMap = requestRepository
                .findAllByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(
                        request -> request.getEvent().getId(),
                        Collectors.counting()
                ));

        LocalDateTime earliestStart = events.stream()
                .map(e -> e.getPublishedOn() != null ? e.getPublishedOn() : e.getCreatedOn())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        List<ViewStatsDto> stats = statsClient.getStats(earliestStart, LocalDateTime.now(), uris, false);
        Map<Long, Long> viewsMap = stats.stream()
                .collect(Collectors.toMap(
                        v -> Long.parseLong(v.getUri().substring(v.getUri().lastIndexOf('/') + 1)),
                        ViewStatsDto::getHits,
                        (a, b) -> a
                ));

        return events.stream()
                .map(e -> EventMapper.toFullDto(
                        e,
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }
}