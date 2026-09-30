package ru.trainlog.workout;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    @EntityGraph(attributePaths = "exercise")
    List<WorkoutSet> findByWorkoutIdOrderById(Long workoutId);

    Optional<WorkoutSet> findByIdAndWorkoutAccountId(Long id, Long accountId);
}
