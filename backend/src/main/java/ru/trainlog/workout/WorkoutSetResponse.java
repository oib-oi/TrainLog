package ru.trainlog.workout;

import java.math.BigDecimal;

public record WorkoutSetResponse(
        Long id,
        Long workoutId,
        Long exerciseId,
        String exerciseName,
        Integer reps,
        BigDecimal weightKg
) {

    static WorkoutSetResponse from(WorkoutSet workoutSet) {
        return new WorkoutSetResponse(
                workoutSet.getId(),
                workoutSet.getWorkout().getId(),
                workoutSet.getExercise().getId(),
                workoutSet.getExercise().getName(),
                workoutSet.getReps(),
                workoutSet.getWeightKg()
        );
    }
}
