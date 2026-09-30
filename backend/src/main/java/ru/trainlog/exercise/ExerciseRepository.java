package ru.trainlog.exercise;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    @Query("select e from Exercise e where e.accountId is null or e.accountId = :accountId order by e.name")
    List<Exercise> findAllVisibleTo(Long accountId);

    @Query("select e from Exercise e where e.id = :id and (e.accountId is null or e.accountId = :accountId)")
    Optional<Exercise> findVisibleTo(Long id, Long accountId);

    @Query("""
            select count(e) > 0 from Exercise e
            where lower(e.name) = lower(:name) and (e.accountId is null or e.accountId = :accountId)
            """)
    boolean isNameTaken(String name, Long accountId);

    @Query("""
            select count(e) > 0 from Exercise e
            where lower(e.name) = lower(:name) and (e.accountId is null or e.accountId = :accountId) and e.id <> :id
            """)
    boolean isNameTakenByOther(String name, Long accountId, Long id);

    @Query("select count(s) > 0 from WorkoutSet s where s.exercise.id = :id")
    boolean isUsedInSets(Long id);
}
