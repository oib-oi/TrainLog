package ru.trainlog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректный адрес почты")
        @Size(max = 100, message = "Почта не длиннее 100 символов")
        String email,

        @NotNull(message = "Укажите пароль")
        @Size(min = 8, max = 72, message = "Пароль от 8 до 72 символов")
        String password
) {
}
