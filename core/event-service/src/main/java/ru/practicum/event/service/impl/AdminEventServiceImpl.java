package ru.practicum.event.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.dto.eventDto.EventState;
import ru.practicum.dto.eventDto.UpdateEventAdminRequest;
import ru.practicum.event.mapper.LocationMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.AdminEventService;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminEventServiceImpl implements AdminEventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final StatsClient statsClient;

    @Override
    public List<Event> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                                      LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        log.info("Админский поиск событий");

        Pageable pageable = PageRequest.of(from / size, size);
        return eventRepository.findAdminEvents(users, states, categories, rangeStart, rangeEnd, pageable);
    }

    @Override
    @Transactional
    public Event updateAdminEvent(Long eventId, UpdateEventAdminRequest dto) {
        log.info("Админское обновление события {}", eventId);

        Event event = findEventById(eventId);
        Category category = findCategoryIfPresent(dto.getCategory());

        applyUpdateFields(event, dto, category);
        applyStateAction(event, dto.getStateAction());

        Event updatedEvent = eventRepository.save(event);
        log.info("Событие {} обновлено администратором", eventId);

        return updatedEvent;
    }

    @Override
    public Long getViewsForEvent(Event event) {
        if (event == null) {
            return 0L;
        }

        LocalDateTime start = event.getPublishedOn() != null ?
                event.getPublishedOn() :
                event.getCreatedOn();

        if (start == null) {
            start = LocalDateTime.now().minusYears(10);
        }

        List<String> uris = List.of("/events/" + event.getId());
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now(), uris, true);

        return stats.isEmpty() ? 0L : stats.getFirst().getHits();
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
    }

    private Category findCategoryIfPresent(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + categoryId + " не найдена"));
    }

    private void applyUpdateFields(Event event, UpdateEventAdminRequest dto, Category category) {
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            event.setEventDate(dto.getEventDate());
        }
        if (category != null) {
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(LocationMapper.toLocation(dto.getLocation()));
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
    }

    private void applyStateAction(Event event, String stateAction) {
        if (stateAction == null) {
            return;
        }

        switch (stateAction) {
            case "PUBLISH_EVENT":
                publishEvent(event);
                break;
            case "REJECT_EVENT":
                rejectEvent(event);
                break;
            default:
                throw new IllegalArgumentException("Некорректное действие: " + stateAction);
        }
    }

    private void publishEvent(Event event) {
        validateEventCanBePublished(event);

        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(LocalDateTime.now());
        log.info("Событие {} опубликовано", event.getId());
    }

    private void validateEventCanBePublished(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException("Опубликовать можно только событие в состоянии ожидания");
        }

        if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ConditionsNotMetException("Дата начала события должна быть не ранее чем через час от публикации");
        }
    }

    private void rejectEvent(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя отклонить уже опубликованное событие");
        }

        event.setState(EventState.CANCELED);
        log.info("Событие {} отклонено", event.getId());
    }
}