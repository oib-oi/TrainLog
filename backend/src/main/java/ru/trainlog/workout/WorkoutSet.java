package ru.trainlog.workout;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.trainlog.exercise.Exercise;

@Entity
@Table(name = "workout_set")
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id")
    private Workout workout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(nullable = false)
    private Integer reps;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    protected WorkoutSet() {
    }

    public WorkoutSet(Workout workout, Exercise exercise, Integer reps, BigDecimal weightKg) {
        this.workout = workout;
        update(exercise, reps, weightKg);
    }

    public void update(Exercise exercise, Integer reps, BigDecimal weightKg) {
        this.exercise = exercise;
        this.reps = reps;
        this.weightKg = weightKg;
    }

    public Long getId() {
        return id;
    }

    public Workout getWorkout() {
        return workout;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public Integer getReps() {
        return reps;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }
}
