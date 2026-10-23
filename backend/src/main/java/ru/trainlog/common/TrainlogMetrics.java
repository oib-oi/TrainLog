package ru.trainlog.common;

import java.math.BigDecimal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class TrainlogMetrics {

    private final Counter registrations;
    private final Counter successfulLogins;
    private final Counter failedLogins;
    private final Counter exercisesCreated;
    private final Counter workoutsCreated;
    private final Counter setsCreated;
    private final Counter liftedWeight;

    public TrainlogMetrics(MeterRegistry registry) {
        registrations = Counter.builder("trainlog.registrations")
                .description("Зарегистрированные аккаунты")
                .register(registry);
        successfulLogins = logins(registry, "success");
        failedLogins = logins(registry, "failure");
        exercisesCreated = Counter.builder("trainlog.exercises")
                .description("Упражнения, добавленные пользователями в свой справочник")
                .register(registry);
        workoutsCreated = Counter.builder("trainlog.workouts")
                .description("Созданные тренировки")
                .register(registry);
        setsCreated = Counter.builder("trainlog.sets")
                .description("Добавленные подходы")
                .register(registry);
        liftedWeight = Counter.builder("trainlog.lifted.weight")
                .baseUnit("kg")
                .description("Суммарный поднятый вес в добавленных подходах: повторения × вес")
                .register(registry);
    }

    private static Counter logins(MeterRegistry registry, String result) {
        return Counter.builder("trainlog.logins")
                .description("Попытки входа")
                .tag("result", result)
                .register(registry);
    }

    public void registered() {
        registrations.increment();
    }

    public void loginSucceeded() {
        successfulLogins.increment();
    }

    public void loginFailed() {
        failedLogins.increment();
    }

    public void exerciseCreated() {
        exercisesCreated.increment();
    }

    public void workoutCreated() {
        workoutsCreated.increment();
    }

    public void setCreated(int reps, BigDecimal weightKg) {
        setsCreated.increment();
        if (weightKg != null) {
            liftedWeight.increment(weightKg.doubleValue() * reps);
        }
    }
}
