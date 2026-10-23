package ru.trainlog.exercise;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.ConflictException;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.TrainlogMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final Long ACCOUNT_ID = 42L;

    @Mock
    private ExerciseRepository repository;

    @Mock
    private CurrentAccount currentAccount;

    @Mock
    private TrainlogMetrics metrics;

    @InjectMocks
    private ExerciseService service;

    @BeforeEach
    void signIn() {
        when(currentAccount.id()).thenReturn(ACCOUNT_ID);
    }

    @Test
    void findAllReturnsCommonAndOwnExercises() {
        when(repository.findAllVisibleTo(ACCOUNT_ID)).thenReturn(List.of(
                new Exercise(null, "Берпи", MuscleGroup.CARDIO, null),
                new Exercise(ACCOUNT_ID, "Жим лёжа", MuscleGroup.CHEST, "Штанга")
        ));

        assertThat(service.findAll()).containsExactly(
                new ExerciseResponse(null, "Берпи", MuscleGroup.CARDIO, null, true),
                new ExerciseResponse(null, "Жим лёжа", MuscleGroup.CHEST, "Штанга", false)
        );
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findVisibleTo(7L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: упражнение #7");
    }

    @Test
    void createStripsName() {
        when(repository.save(any(Exercise.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new ExerciseRequest("  Выпады ", MuscleGroup.LEGS, "С гантелями"));

        assertThat(response).isEqualTo(new ExerciseResponse(null, "Выпады", MuscleGroup.LEGS, "С гантелями", false));
        verify(repository).isNameTaken("Выпады", ACCOUNT_ID);
        verify(metrics).exerciseCreated();
    }

    @Test
    void createRejectsTakenName() {
        when(repository.isNameTaken("Выпады", ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ExerciseRequest("Выпады", MuscleGroup.LEGS, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Упражнение «Выпады» уже есть в справочнике");
        verify(repository, never()).save(any());
        verify(metrics, never()).exerciseCreated();
    }

    @Test
    void updateChangesExercise() {
        var exercise = new Exercise(ACCOUNT_ID, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));

        var response = service.update(1L, new ExerciseRequest(" Планка ", MuscleGroup.CORE, "На локтях"));

        assertThat(response).isEqualTo(new ExerciseResponse(null, "Планка", MuscleGroup.CORE, "На локтях", false));
        assertThat(exercise.getName()).isEqualTo("Планка");
        assertThat(exercise.getMuscleGroup()).isEqualTo(MuscleGroup.CORE);
        assertThat(exercise.getDescription()).isEqualTo("На локтях");
    }

    @Test
    void updateRejectsNameOfAnotherExercise() {
        var exercise = new Exercise(ACCOUNT_ID, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));
        when(repository.isNameTakenByOther("Планка", ACCOUNT_ID, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new ExerciseRequest("Планка", MuscleGroup.CORE, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(exercise.getName()).isEqualTo("Выпады");
    }

    @Test
    void updateRejectsCommonExercise() {
        var exercise = new Exercise(null, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));

        assertThatThrownBy(() -> service.update(1L, new ExerciseRequest("Планка", MuscleGroup.CORE, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Упражнение «Выпады» из общего справочника, его нельзя менять");
        assertThat(exercise.getName()).isEqualTo("Выпады");
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new ExerciseRequest("Планка", MuscleGroup.CORE, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesUnusedExercise() {
        var exercise = new Exercise(ACCOUNT_ID, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));

        service.delete(1L);

        verify(repository).delete(exercise);
    }

    @Test
    void deleteRejectsExerciseUsedInSets() {
        var exercise = new Exercise(ACCOUNT_ID, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));
        when(repository.isUsedInSets(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Упражнение «Выпады» есть в подходах, его нельзя удалить");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRejectsCommonExercise() {
        var exercise = new Exercise(null, "Выпады", MuscleGroup.LEGS, null);
        when(repository.findVisibleTo(1L, ACCOUNT_ID)).thenReturn(Optional.of(exercise));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }
}
