package ru.practicum.main.service.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewUserRequest {

    @NotBlank(message = "Поле email обязательно")
    @Email(message = "Не корректный email")
    @Size(min = 6, max = 254, message = "Имя должно быть от 6 до 64 символов")
    private String email;

    @NotBlank(message = "Имя пользователя обязательно")
    @Size(min = 2, max = 250, message = "Имя должно быть от 2 до 50 символов")
    private String name;
}
