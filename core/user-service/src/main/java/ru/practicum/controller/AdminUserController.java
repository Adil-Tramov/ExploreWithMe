package ru.practicum.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.userDto.NewUserRequest;
import ru.practicum.dto.userDto.UserDto;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminUserController {
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto saveUser(@Valid @RequestBody NewUserRequest request) {
        log.info("POST /admin/users - Создание пользователя: {}", request);
        User user = userService.saveUser(request);
        return UserMapper.toDto(user);

    }

    @GetMapping
    public List<UserDto> getUsers(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  @RequestParam(name = "from", defaultValue = "0") Long from,
                                  @RequestParam(name = "size", defaultValue = "10") Long size) {
        log.info("GET /admin/users - Получение списка пользователей: ids {}, from {}, size {}", ids, from, size);
        return userService.getUsers(ids, from, size)
                .stream()
                .map(UserMapper::toDto)
                .toList();
    }

    @GetMapping("/allUsersById")
    public List<UserDto> getAllUsersById(@RequestParam(name = "ids", required = false) List<Long> ids) {
        log.info("GET /admin/users/allUsersById - Получение списка пользователей: ids {}", ids);
        return userService.getAllUsersById(ids)
                .stream()
                .map(UserMapper::toDto)
                .toList();
    }

    @GetMapping("/{userId}")
    public UserDto getUser(@PathVariable("userId") Long userId) {
        log.info("GET /admin/users/{userId} - Получение пользователя: id {}", userId);
        return UserMapper.toDto(userService.getUser(userId));
    }


    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("userId") Long userId) {
        log.info("DELETE /admin/users/{userId} - Удаление пользователя с ID: {}", userId);
        userService.deleteUser(userId);
    }
}
