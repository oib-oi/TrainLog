package ru.trainlog.workout;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkoutSetRequest(
        @NotNull(message = "Выберите упражнение")
        Long exerciseId,

        @NotNull(message = "Укажите число повторений")
        @Positive(message = "Число повторений должно быть больше нуля")
        Integer reps,

        @PositiveOrZero(message = "Вес не может быть отрицательным")
        @Digits(integer = 4, fraction = 2, message = "Вес не больше 9999,99 кг с точностью до сотых")
        BigDecimal weightKg
) {
}
