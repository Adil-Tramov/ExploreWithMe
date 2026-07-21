package ru.practicum.faign;

import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.userDto.NewUserRequest;
import ru.practicum.dto.userDto.UserDto;

import java.util.List;

@FeignClient(name = "user-service", path = "/admin/users", configuration = FeignConfig.class)
public interface UserServiceFeign {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserDto saveUser(@Valid @RequestBody NewUserRequest request);

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    List<UserDto> getUsers(@RequestParam(name = "ids", required = false) List<Long> ids,
                           @RequestParam(name = "from", defaultValue = "0") Long from,
                           @RequestParam(name = "size", defaultValue = "10") Long size);

    @GetMapping("/allUsersById")
    @ResponseStatus(HttpStatus.OK)
    List<UserDto> getAllUsersById(@RequestParam(name = "ids", required = false) List<Long> ids);

    @GetMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    UserDto getUser(@PathVariable("userId") Long userId);

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(@PathVariable("userId") Long userId);
}
