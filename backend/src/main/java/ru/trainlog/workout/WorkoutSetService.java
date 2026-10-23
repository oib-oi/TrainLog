package ru.trainlog.workout;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.TrainlogMetrics;
import ru.trainlog.exercise.Exercise;
import ru.trainlog.exercise.ExerciseRepository;

@Service
@Transactional
public class WorkoutSetService {

    private final WorkoutSetRepository setRepository;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurrentAccount currentAccount;
    private final TrainlogMetrics metrics;

    public WorkoutSetService(WorkoutSetRepository setRepository,
                             WorkoutRepository workoutRepository,
                             ExerciseRepository exerciseRepository,
                             CurrentAccount currentAccount,
                             TrainlogMetrics metrics) {
        this.setRepository = setRepository;
        this.workoutRepository = workoutRepository;
        this.exerciseRepository = exerciseRepository;
        this.currentAccount = currentAccount;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<WorkoutSetResponse> findByWorkout(Long workoutId) {
        getWorkout(workoutId);
        return setRepository.findByWorkoutIdOrderById(workoutId).stream()
                .map(WorkoutSetResponse::from)
                .toList();
    }

    public WorkoutSetResponse create(Long workoutId, WorkoutSetRequest request) {
        var workoutSet = new WorkoutSet(
                getWorkout(workoutId),
                getExercise(request.exerciseId()),
                request.reps(),
                request.weightKg()
        );
        var saved = setRepository.save(workoutSet);
        metrics.setCreated(request.reps(), request.weightKg());
        return WorkoutSetResponse.from(saved);
    }

    public WorkoutSetResponse update(Long id, WorkoutSetRequest request) {
        var workoutSet = get(id);
        workoutSet.update(getExercise(request.exerciseId()), request.reps(), request.weightKg());
        return WorkoutSetResponse.from(workoutSet);
    }

    public void delete(Long id) {
        setRepository.delete(get(id));
    }

    private WorkoutSet get(Long id) {
        return setRepository.findByIdAndWorkoutAccountId(id, currentAccount.id())
                .orElseThrow(() -> new NotFoundException("подход", id));
    }

    private Workout getWorkout(Long id) {
        return workoutRepository.findByIdAndAccountId(id, currentAccount.id())
                .orElseThrow(() -> new NotFoundException("тренировка", id));
    }

    private Exercise getExercise(Long id) {
        return exerciseRepository.findVisibleTo(id, currentAccount.id())
                .orElseThrow(() -> new NotFoundException("упражнение", id));
    }
}
