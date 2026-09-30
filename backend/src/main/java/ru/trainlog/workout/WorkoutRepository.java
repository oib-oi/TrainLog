package ru.trainlog.workout;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    Page<Workout> findByAccountId(Long accountId, Pageable pageable);

    Optional<Workout> findByIdAndAccountId(Long id, Long accountId);
}
