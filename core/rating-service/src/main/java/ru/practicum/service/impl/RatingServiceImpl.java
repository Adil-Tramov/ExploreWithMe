package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.eventDto.EventFullDto;
import ru.practicum.dto.ratingDto.EventRatingListDto;
import ru.practicum.dto.ratingDto.EventRatingRequestDto;
import ru.practicum.dto.ratingDto.EventRatingResponseDto;
import ru.practicum.dto.ratingDto.EventRatingStatsDto;
import ru.practicum.exception.ConditionsNotMetException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RatingMapper;
import ru.practicum.model.EventRating;
import ru.practicum.repository.EventRatingRepository;
import ru.practicum.service.RatingService;

import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final EventRatingRepository ratingRepository;

    @Override
    @Transactional
    public EventRating addLike(Long userId, Long eventId, EventRatingRequestDto dto,
                               EventFullDto event, boolean userAttended) {
        log.info("Добавление LIKE для события id={} пользователем id={}", eventId, userId);
        return addRating(userId, eventId, dto, EventRating.RatingType.LIKE, event, userAttended);
    }

    @Override
    @Transactional
    public EventRating addDislike(Long userId, Long eventId, EventRatingRequestDto dto,
                                  EventFullDto event, boolean userAttended) {
        log.info("Добавление DISLIKE для события id={} пользователем id={}", eventId, userId);
        return addRating(userId, eventId, dto, EventRating.RatingType.DISLIKE, event, userAttended);
    }

    private EventRating addRating(Long userId,
                                  Long eventId,
                                  EventRatingRequestDto dto,
                                  EventRating.RatingType type,
                                  EventFullDto event,
                                  boolean userAttended) {

        validateRatingConditions(event, dto, userAttended);

        EventRating existingRating = ratingRepository.findByEventIdAndUserId(eventId, userId).orElse(null);

        if (existingRating != null) {
            if (existingRating.getRatingType() == type) {
                throw new ConditionsNotMetException("Вы уже оценили это событие");
            }
            existingRating.setRatingType(type);
            existingRating.setCreated(dto.getTimestamp());
            ratingRepository.save(existingRating);
            log.debug("Оценка обновлена: {}", existingRating);
            return existingRating;
        }

        EventRating newRating = RatingMapper.toEntity(eventId, userId, type, dto.getTimestamp());
        newRating = ratingRepository.save(newRating);
        log.debug("Создана новая оценка: {}", newRating);

        return newRating;
    }

    @Override
    @Transactional
    public void deleteRating(Long userId, Long eventId) {
        log.info("Удаление оценки для события id={} пользователем id={}", eventId, userId);

        EventRating rating = ratingRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Оценка не найдена для события id=" + eventId + " и пользователя id=" + userId));

        ratingRepository.delete(rating);
        log.debug("Оценка удалена");
    }

    @Override
    public EventRatingStatsDto getEventRatingStats(Long eventId) {
        log.info("Получение статистики рейтинга события id={}", eventId);

        List<Object[]> ratingData = ratingRepository.getRatingStatsByEventId(eventId);

        if (ratingData.isEmpty()) {
            return RatingMapper.toStatsDto(eventId, 0L, 0L);
        }

        Object[] data = ratingData.get(0);
        Long likes = ((Number) data[1]).longValue();
        Long dislikes = ((Number) data[2]).longValue();

        return RatingMapper.toStatsDto(eventId, likes, dislikes);
    }

    @Override
    public EventRatingListDto getUserRatings(Long userId, String rating, int from, int size) {
        log.info("Получение оценок пользователя id={}, rating={}, from={}, size={}", userId, rating, from, size);

        Pageable pageable = PageRequest.of(from / size, size);
        List<EventRating> ratings;
        long totalElements;

        if (rating == null || rating.isEmpty()) {
            ratings = ratingRepository.findByUserIdOrderByCreatedDesc(userId, pageable);
            totalElements = ratingRepository.countByUserId(userId);
        } else if ("like".equalsIgnoreCase(rating)) {
            ratings = ratingRepository.findByUserIdAndRatingTypeOrderByCreatedDesc(
                    userId, EventRating.RatingType.LIKE, pageable);
            totalElements = ratingRepository.countByUserIdAndRatingType(userId, EventRating.RatingType.LIKE);
        } else if ("dislike".equalsIgnoreCase(rating)) {
            ratings = ratingRepository.findByUserIdAndRatingTypeOrderByCreatedDesc(
                    userId, EventRating.RatingType.DISLIKE, pageable);
            totalElements = ratingRepository.countByUserIdAndRatingType(userId, EventRating.RatingType.DISLIKE);
        } else {
            throw new ConditionsNotMetException("Параметр rating должен быть 'like' или 'dislike'");
        }

        List<EventRatingResponseDto> ratingDtos = RatingMapper.toResponseDtoList(ratings);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return EventRatingListDto.builder()
                .ratings(ratingDtos)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(from / size)
                .pageSize(size)
                .build();
    }

    @Override
    public List<Object[]> getTopRatedEvents(int from, int size, String order) {
        log.info("Получение топа событий, from={}, size={}, order={}", from, size, order);

        Sort.Direction direction;
        if (order == null || "DESC".equalsIgnoreCase(order)) {
            direction = Sort.Direction.DESC;
        } else if ("ASC".equalsIgnoreCase(order)) {
            direction = Sort.Direction.ASC;
        } else {
            throw new ConditionsNotMetException("Параметр order должен быть 'ASC' или 'DESC'");
        }

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(direction, "rating"));

        return ratingRepository.findTopRatedEvents(pageable);
    }

    private void validateRatingConditions(EventFullDto event, EventRatingRequestDto dto, boolean userAttended) {
        if (event.getEventDate().isAfter(dto.getTimestamp())) {
            throw new ConditionsNotMetException("Нельзя оценивать событие, которое ещё не состоялось");
        }

        if (!userAttended) {
            throw new ConditionsNotMetException("Пользователь не посещал данное событие");
        }
    }
}