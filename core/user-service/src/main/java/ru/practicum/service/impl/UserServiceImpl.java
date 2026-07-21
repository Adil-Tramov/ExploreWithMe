package ru.practicum.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.userDto.NewUserRequest;
import ru.practicum.exception.AlreadyExistsException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.UserService;

import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Transactional
    public User saveUser(NewUserRequest request) {
        log.info("Создание нового пользователя: {}", request);
        userEmailCheck(request);
        User user = UserMapper.toEntity(request);
        user = userRepository.save(user);
        log.info("Создан пользователь с id={}", user.getId());
        return user;
    }

    public List<User> getUsers(List<Long> ids, Long from, Long size) {
        if (ids != null) {
            log.info("Получение списка пользователей с ids: {}", ids);
            List<User> users = userRepository.getUsers(ids);
            log.info("Список по ids: {}", users);
            return users;
        } else {
            log.info("Получение списка из первых {} пользователей с позиции {}: ", size, from);
            return userRepository.getUsers(from, size);
        }
    }

    public List<User> getAllUsersById(List<Long> ids) {
        return userRepository.findAllById(ids);
    }

    public User getUser(Long userId) {
        log.info("Получение пользователя с id: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id: " + userId + " не существует"));
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Удаление пользователя с ID: {}", id);
        userRepository.deleteById(id);
    }

    private void userEmailCheck(NewUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException("Пользователь с адресом '" + request.getEmail() + "' уже существует");
        }
    }

}
