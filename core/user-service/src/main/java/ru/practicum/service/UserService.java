package ru.practicum.service;

import ru.practicum.dto.userDto.NewUserRequest;
import ru.practicum.model.User;

import java.util.List;

public interface UserService {

    User saveUser(NewUserRequest request);

    List<User> getUsers(List<Long> ids, Long from, Long size);

    List<User> getAllUsersById(List<Long> ids);

    User getUser(Long userId);

    void deleteUser(Long id);
}
