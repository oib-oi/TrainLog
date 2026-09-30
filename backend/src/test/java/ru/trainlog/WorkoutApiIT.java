package ru.trainlog;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import ru.trainlog.auth.Account;
import ru.trainlog.exercise.Exercise;
import ru.trainlog.exercise.MuscleGroup;
import ru.trainlog.workout.Workout;
import ru.trainlog.workout.WorkoutSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkoutApiIT extends IntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    @Test
    void createsAndReturnsWorkout() throws Exception {
        mvc.perform(post("/api/workouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-29", "title": "Ноги", "notes": "Тяжело", "durationMinutes": 60}
                        """))
                .andExpect(status().isCreated());

        var saved = workouts.findAll().getFirst();
        mvc.perform(get("/api/workouts/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performedOn").value("2026-09-29"))
                .andExpect(jsonPath("$.title").value("Ноги"))
                .andExpect(jsonPath("$.notes").value("Тяжело"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    @Test
    void listsNewestWorkoutsFirst() throws Exception {
        workouts.save(new Workout(account.getId(), DATE.minusDays(1), "Вчера", null, null));
        workouts.save(new Workout(account.getId(), DATE, "Утро", null, null));
        workouts.save(new Workout(account.getId(), DATE, "Вечер", null, null));
        workouts.save(new Workout(account.getId(), DATE.minusDays(7), "Неделю назад", null, null));

        mvc.perform(get("/api/workouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains("Вечер", "Утро", "Вчера", "Неделю назад")));
    }

    @Test
    void listsWorkoutsByPages() throws Exception {
        for (int day = 0; day < 5; day++) {
            workouts.save(new Workout(account.getId(), DATE.minusDays(day), "День " + day, null, null));
        }

        mvc.perform(get("/api/workouts").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains("День 0", "День 1")))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/workouts").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains("День 4")));
        mvc.perform(get("/api/workouts").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void updatesWorkout() throws Exception {
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", "Тяжело", 60));

        mvc.perform(put("/api/workouts/{id}", workout.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-30", "title": "Спина"}
                        """))
                .andExpect(status().isOk());

        var updated = workouts.findById(workout.getId()).orElseThrow();
        assertThat(updated.getPerformedOn()).isEqualTo(DATE.plusDays(1));
        assertThat(updated.getTitle()).isEqualTo("Спина");
        assertThat(updated.getNotes()).isNull();
        assertThat(updated.getDurationMinutes()).isNull();
    }

    @Test
    void deletesWorkoutWithItsSets() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));
        var other = workouts.save(new Workout(account.getId(), DATE, "Спина", null, null));
        sets.save(new WorkoutSet(workout, exercise, 10, null));
        sets.save(new WorkoutSet(workout, exercise, 8, null));
        var kept = sets.save(new WorkoutSet(other, exercise, 5, null));

        mvc.perform(delete("/api/workouts/{id}", workout.getId()))
                .andExpect(status().isNoContent());

        assertThat(workouts.existsById(workout.getId())).isFalse();
        assertThat(sets.count()).isEqualTo(1);
        assertThat(sets.existsById(kept.getId())).isTrue();
        assertThat(exercises.existsById(exercise.getId())).isTrue();
    }

    @Test
    void addsSetsAndListsThemInOrder() throws Exception {
        var squat = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var lunge = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));

        mvc.perform(post("/api/workouts/{id}/sets", workout.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 8, "weightKg": 100.5}
                                """.formatted(squat.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workoutId").value(workout.getId()))
                .andExpect(jsonPath("$.exerciseName").value("Приседания"));
        mvc.perform(post("/api/workouts/{id}/sets", workout.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 12}
                                """.formatted(lunge.getId())))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/workouts/{id}/sets", workout.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].exerciseName").value(contains("Приседания", "Выпады")))
                .andExpect(jsonPath("$[0].reps").value(8))
                .andExpect(jsonPath("$[0].weightKg").value(100.5))
                .andExpect(jsonPath("$[1].weightKg").isEmpty());
    }

    @Test
    void listsOnlySetsOfRequestedWorkout() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));
        var other = workouts.save(new Workout(account.getId(), DATE, "Спина", null, null));
        sets.save(new WorkoutSet(workout, exercise, 10, null));
        sets.save(new WorkoutSet(other, exercise, 5, null));

        mvc.perform(get("/api/workouts/{id}/sets", workout.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reps").value(10));
    }

    @Test
    void rejectsSetForUnknownWorkoutOrExercise() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));

        mvc.perform(post("/api/workouts/{id}/sets", workout.getId() + 1).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 8}
                                """.formatted(exercise.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/workouts/{id}/sets", workout.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 8}
                                """.formatted(exercise.getId() + 1)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workouts/{id}/sets", workout.getId() + 1))
                .andExpect(status().isNotFound());

        assertThat(sets.count()).isZero();
    }

    @Test
    void updatesSet() throws Exception {
        var squat = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var lunge = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));
        var workoutSet = sets.save(new WorkoutSet(workout, squat, 10, new BigDecimal("80.00")));

        mvc.perform(put("/api/sets/{id}", workoutSet.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 15, "weightKg": 12.5}
                                """.formatted(lunge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workoutId").value(workout.getId()))
                .andExpect(jsonPath("$.exerciseName").value("Выпады"));

        mvc.perform(get("/api/workouts/{id}/sets", workout.getId()))
                .andExpect(jsonPath("$[0].exerciseId").value(lunge.getId()))
                .andExpect(jsonPath("$[0].reps").value(15))
                .andExpect(jsonPath("$[0].weightKg").value(12.5));
    }

    @Test
    void deletesSet() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));
        var workoutSet = sets.save(new WorkoutSet(workout, exercise, 10, null));

        mvc.perform(delete("/api/sets/{id}", workoutSet.getId()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/sets/{id}", workoutSet.getId()))
                .andExpect(status().isNotFound());

        assertThat(workouts.existsById(workout.getId())).isTrue();
    }

    @Test
    void hidesWorkoutsAndSetsOfOtherAccounts() throws Exception {
        var exercise = exercises.save(new Exercise(null, "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));
        var workoutSet = sets.save(new WorkoutSet(workout, exercise, 10, null));
        signIn(accounts.save(new Account("other@example.com", "hash")));

        mvc.perform(get("/api/workouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/workouts/{id}", workout.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workouts/{id}", workout.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workouts/{id}/sets", workout.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/workouts/{id}/sets", workout.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 8}
                                """.formatted(exercise.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/sets/{id}", workoutSet.getId()))
                .andExpect(status().isNotFound());

        assertThat(sets.count()).isEqualTo(1);
    }

    @Test
    void rejectsSetWithExerciseOfAnotherAccount() throws Exception {
        var other = accounts.save(new Account("other@example.com", "hash"));
        var exercise = exercises.save(new Exercise(other.getId(), "Приседания", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), DATE, "Ноги", null, null));

        mvc.perform(post("/api/workouts/{id}/sets", workout.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": %d, "reps": 8}
                                """.formatted(exercise.getId())))
                .andExpect(status().isNotFound());
    }
}
