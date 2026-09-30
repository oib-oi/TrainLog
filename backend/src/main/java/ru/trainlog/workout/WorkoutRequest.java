package ru.trainlog.workout;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkoutRequest(
        @NotNull(message = "Укажите дату")
        LocalDate performedOn,

        @NotBlank(message = "Укажите название")
        @Size(max = 100, message = "Название не длиннее 100 символов")
        String title,

        @Size(max = 500, message = "Заметки не длиннее 500 символов")
        String notes,

        @Positive(message = "Длительность должна быть больше нуля")
        Integer durationMinutes
) {
}
