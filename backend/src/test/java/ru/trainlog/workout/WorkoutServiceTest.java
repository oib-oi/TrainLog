package ru.trainlog.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.PageResponse;
import ru.trainlog.common.TrainlogMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    @Mock
    private WorkoutRepository repository;

    @Mock
    private CurrentAccount currentAccount;

    @Mock
    private TrainlogMetrics metrics;

    @InjectMocks
    private WorkoutService service;

    @BeforeEach
    void signIn() {
        when(currentAccount.id()).thenReturn(ACCOUNT_ID);
    }

    @Test
    void findAllReturnsPageNewestFirst() {
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "performedOn", "id"));
        when(repository.findByAccountId(ACCOUNT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(new Workout(ACCOUNT_ID, DATE, "Ноги", null, 60)), pageable, 3));

        assertThat(service.findAll(1, 2)).isEqualTo(
                new PageResponse<>(List.of(new WorkoutResponse(null, DATE, "Ноги", null, 60)), 1, 2, 3, 2));
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findByIdAndAccountId(3L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(3L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Не найдено: тренировка #3");
    }

    @Test
    void createSavesWorkout() {
        when(repository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new WorkoutRequest(DATE, "Ноги", "Тяжело", 60));

        assertThat(response).isEqualTo(new WorkoutResponse(null, DATE, "Ноги", "Тяжело", 60));
        verify(metrics).workoutCreated();
    }

    @Test
    void updateChangesWorkout() {
        var workout = new Workout(ACCOUNT_ID, DATE, "Ноги", "Тяжело", 60);
        when(repository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.of(workout));

        var response = service.update(1L, new WorkoutRequest(DATE.plusDays(1), "Спина", null, null));

        assertThat(response).isEqualTo(new WorkoutResponse(null, DATE.plusDays(1), "Спина", null, null));
        assertThat(workout.getTitle()).isEqualTo("Спина");
        assertThat(workout.getPerformedOn()).isEqualTo(DATE.plusDays(1));
    }

    @Test
    void deleteRemovesWorkout() {
        var workout = new Workout(ACCOUNT_ID, DATE, "Ноги", null, null);
        when(repository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.of(workout));

        service.delete(1L);

        verify(repository).delete(workout);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findByIdAndAccountId(1L, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
