package ru.trainlog.workout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.TrainlogMetrics;
import ru.trainlog.exercise.Exercise;
import ru.trainlog.exercise.ExerciseRepository;
import ru.trainlog.exercise.MuscleGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutSetServiceTest {

    private static final Long ACCOUNT_ID = 42L;

    private final Workout workout = new Workout(ACCOUNT_ID, LocalDate.of(2026, 9, 29), "Ноги", null, 60);
    private final Exercise squat = new Exercise(ACCOUNT_ID, "Приседания", MuscleGroup.LEGS, null);
    private final Exercise lunge = new Exercise(ACCOUNT_ID, "Выпады", MuscleGroup.LEGS, null);

    @Mock
    private WorkoutSetRepository setRepository;

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private CurrentAccount currentAccount;

    @Mock
    private TrainlogMetrics metrics;

    @InjectMocks
    private WorkoutSetService service;

    @BeforeEach
    void signIn() {
        when(currentAccount.id()).thenReturn(ACCOUNT_ID);
    }

    @Test
    void findByWorkoutReturnsSets() {
        when(workoutRepository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.of(workout));
        when(setRepository.findByWorkoutIdOrderById(1L)).thenReturn(List.of(
                new WorkoutSet(workout, squat, 10, new BigDecimal("80.00")),
                new WorkoutSet(workout, lunge, 12, null)
        ));

        assertThat(service.findByWorkout(1L)).containsExactly(
                new WorkoutSetResponse(null, null, null, "Приседания", 10, new BigDecimal("80.00")),
                new WorkoutSetResponse(null, null, null, "Выпады", 12, null)
        );
    }

    @Test
    void findByWorkoutThrowsWhenWorkoutMissing() {
        when(workoutRepository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByWorkout(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: тренировка #1");
        verify(setRepository, never()).findByWorkoutIdOrderById(any());
    }

    @Test
    void createSavesSet() {
        when(workoutRepository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleTo(2L, ACCOUNT_ID)).thenReturn(Optional.of(squat));
        when(setRepository.save(any(WorkoutSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1L, new WorkoutSetRequest(2L, 10, new BigDecimal("80.5")));

        assertThat(response.exerciseName()).isEqualTo("Приседания");
        assertThat(response.reps()).isEqualTo(10);
        assertThat(response.weightKg()).isEqualByComparingTo("80.5");
        verify(metrics).setCreated(10, new BigDecimal("80.5"));
    }

    @Test
    void createThrowsWhenWorkoutMissing() {
        when(workoutRepository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new WorkoutSetRequest(2L, 10, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: тренировка #1");
        verify(setRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenExerciseMissing() {
        when(workoutRepository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleTo(2L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new WorkoutSetRequest(2L, 10, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: упражнение #2");
        verify(setRepository, never()).save(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void updateChangesSet() {
        var workoutSet = new WorkoutSet(workout, squat, 10, new BigDecimal("80.00"));
        when(setRepository.findByIdAndWorkoutAccountId(5L, ACCOUNT_ID)).thenReturn(Optional.of(workoutSet));
        when(exerciseRepository.findVisibleTo(3L, ACCOUNT_ID)).thenReturn(Optional.of(lunge));

        var response = service.update(5L, new WorkoutSetRequest(3L, 12, null));

        assertThat(response.exerciseName()).isEqualTo("Выпады");
        assertThat(workoutSet.getExercise()).isSameAs(lunge);
        assertThat(workoutSet.getReps()).isEqualTo(12);
        assertThat(workoutSet.getWeightKg()).isNull();
        assertThat(workoutSet.getWorkout()).isSameAs(workout);
    }

    @Test
    void updateThrowsWhenSetMissing() {
        when(setRepository.findByIdAndWorkoutAccountId(5L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new WorkoutSetRequest(3L, 12, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: подход #5");
    }

    @Test
    void deleteRemovesSet() {
        var workoutSet = new WorkoutSet(workout, squat, 10, null);
        when(setRepository.findByIdAndWorkoutAccountId(5L, ACCOUNT_ID)).thenReturn(Optional.of(workoutSet));

        service.delete(5L);

        verify(setRepository).delete(workoutSet);
    }

    @Test
    void deleteThrowsWhenSetMissing() {
        when(setRepository.findByIdAndWorkoutAccountId(5L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(NotFoundException.class);
        verify(setRepository, never()).delete(any());
    }
}
