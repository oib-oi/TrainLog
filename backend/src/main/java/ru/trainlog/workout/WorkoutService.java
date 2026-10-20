package ru.trainlog.workout;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.PageResponse;

@Service
@Transactional
public class WorkoutService {

    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc((@NonNull Workout workout) -> workout.getPerformedOn()),
            Sort.Order.desc((@NonNull Workout workout) -> workout.getId()));

    private final WorkoutRepository repository;
    private final CurrentAccount currentAccount;

    public WorkoutService(WorkoutRepository repository, CurrentAccount currentAccount) {
        this.repository = repository;
        this.currentAccount = currentAccount;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkoutResponse> findAll(int page, int size) {
        var workouts = repository.findByAccountId(currentAccount.id(), PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.from(workouts.map(WorkoutResponse::from));
    }

    @Transactional(readOnly = true)
    public WorkoutResponse findById(Long id) {
        return WorkoutResponse.from(get(id));
    }

    public WorkoutResponse create(WorkoutRequest request) {
        var workout = new Workout(currentAccount.id(),
                request.performedOn(), request.title(), request.notes(), request.durationMinutes());
        return WorkoutResponse.from(repository.save(workout));
    }

    public WorkoutResponse update(Long id, WorkoutRequest request) {
        var workout = get(id);
        workout.update(request.performedOn(), request.title(), request.notes(), request.durationMinutes());
        return WorkoutResponse.from(workout);
    }

    public void delete(Long id) {
        repository.delete(get(id));
    }

    private Workout get(Long id) {
        return repository.findByIdAndAccountId(id, currentAccount.id())
                .orElseThrow(() -> new NotFoundException("тренировка", id));
    }
}
