package ru.trainlog;

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

class ExerciseApiIT extends IntegrationTest {

    @Test
    void createsAndReturnsExercise() throws Exception {
        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "  Выпады ", "muscleGroup": "LEGS", "description": "С гантелями"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Выпады"));

        var saved = exercises.findAll().getFirst();
        mvc.perform(get("/api/exercises/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Выпады"))
                .andExpect(jsonPath("$.muscleGroup").value("LEGS"))
                .andExpect(jsonPath("$.description").value("С гантелями"));
    }

    @Test
    void listsExercisesByName() throws Exception {
        exercises.save(new Exercise(account.getId(), "Планка", MuscleGroup.CORE, null));
        exercises.save(new Exercise(account.getId(), "Берпи", MuscleGroup.CARDIO, null));
        exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(get("/api/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Берпи", "Выпады", "Планка")));
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() throws Exception {
        exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "ВЫПАДЫ", "muscleGroup": "LEGS"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Упражнение «ВЫПАДЫ» уже есть в справочнике"));

        assertThat(exercises.count()).isEqualTo(1);
    }

    @Test
    void updatesExerciseKeepingItsName() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(put("/api/exercises/{id}", exercise.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "выпады", "muscleGroup": "CARDIO", "description": "В движении"}
                        """))
                .andExpect(status().isOk());

        var updated = exercises.findById(exercise.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("выпады");
        assertThat(updated.getMuscleGroup()).isEqualTo(MuscleGroup.CARDIO);
        assertThat(updated.getDescription()).isEqualTo("В движении");
    }

    @Test
    void rejectsRenameToExistingName() throws Exception {
        exercises.save(new Exercise(account.getId(), "Планка", MuscleGroup.CORE, null));
        var exercise = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(put("/api/exercises/{id}", exercise.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "планка", "muscleGroup": "LEGS"}
                        """))
                .andExpect(status().isConflict());

        assertThat(exercises.findById(exercise.getId()).orElseThrow().getName()).isEqualTo("Выпады");
    }

    @Test
    void deletesUnusedExercise() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(delete("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsExerciseUsedInSets() throws Exception {
        var exercise = exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));
        var workout = workouts.save(new Workout(account.getId(), LocalDate.of(2026, 9, 29), "Ноги", null, null));
        sets.save(new WorkoutSet(workout, exercise, 10, null));

        mvc.perform(delete("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Упражнение «Выпады» есть в подходах, его нельзя удалить"));

        assertThat(exercises.existsById(exercise.getId())).isTrue();
    }

    @Test
    void listsCommonAndOwnExercisesOnly() throws Exception {
        var other = accounts.save(new Account("other@example.com", "hash"));
        exercises.save(new Exercise(null, "Берпи", MuscleGroup.CARDIO, null));
        exercises.save(new Exercise(account.getId(), "Выпады", MuscleGroup.LEGS, null));
        exercises.save(new Exercise(other.getId(), "Планка", MuscleGroup.CORE, null));

        mvc.perform(get("/api/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Берпи", "Выпады")))
                .andExpect(jsonPath("$[*].common").value(contains(true, false)));
    }

    @Test
    void allowsSameNameInDifferentAccounts() throws Exception {
        var other = accounts.save(new Account("other@example.com", "hash"));
        exercises.save(new Exercise(other.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Выпады", "muscleGroup": "LEGS"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsNameOfCommonExercise() throws Exception {
        exercises.save(new Exercise(null, "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "выпады", "muscleGroup": "LEGS"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void keepsCommonExerciseUnchanged() throws Exception {
        var exercise = exercises.save(new Exercise(null, "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(put("/api/exercises/{id}", exercise.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Планка", "muscleGroup": "CORE"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Упражнение «Выпады» из общего справочника, его нельзя менять"));
        mvc.perform(delete("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isConflict());

        assertThat(exercises.findById(exercise.getId()).orElseThrow().getName()).isEqualTo("Выпады");
    }

    @Test
    void hidesExercisesOfOtherAccounts() throws Exception {
        var other = accounts.save(new Account("other@example.com", "hash"));
        var exercise = exercises.save(new Exercise(other.getId(), "Выпады", MuscleGroup.LEGS, null));

        mvc.perform(get("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/exercises/{id}", exercise.getId()))
                .andExpect(status().isNotFound());

        assertThat(exercises.existsById(exercise.getId())).isTrue();
    }
}
