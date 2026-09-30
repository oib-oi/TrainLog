package ru.trainlog.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record LoginRequest(
        @NotBlank(message = "Укажите почту")
        String email,

        @NotEmpty(message = "Укажите пароль")
        String password
) {
}
