package ru.trainlog.exercise;

public record ExerciseResponse(Long id, String name, MuscleGroup muscleGroup, String description, boolean common) {

    static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getMuscleGroup(),
                exercise.getDescription(),
                exercise.isCommon()
        );
    }
}
