package ru.practicum.main.service.user.service;

import ru.practicum.main.service.user.dto.NewUserRequest;
import ru.practicum.main.service.user.dto.UserDto;

import java.util.List;

public interface UserService {

    UserDto saveUser(NewUserRequest request);

    List<UserDto> getUsers(List<Long> ids, Long from, Long size);

    void deleteUser(Integer id);
}
