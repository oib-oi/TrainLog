package ru.trainlog.exercise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExerciseRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 100, message = "Название не длиннее 100 символов")
        String name,

        @NotNull(message = "Укажите группу мышц")
        MuscleGroup muscleGroup,

        @Size(max = 500, message = "Описание не длиннее 500 символов")
        String description
) {
}
