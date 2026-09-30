package ru.trainlog.workout;

import java.time.LocalDate;

public record WorkoutResponse(Long id, LocalDate performedOn, String title, String notes, Integer durationMinutes) {

    static WorkoutResponse from(Workout workout) {
        return new WorkoutResponse(
                workout.getId(),
                workout.getPerformedOn(),
                workout.getTitle(),
                workout.getNotes(),
                workout.getDurationMinutes()
        );
    }
}
