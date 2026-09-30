package ru.trainlog.exercise;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.trainlog.auth.CurrentAccount;
import ru.trainlog.common.ConflictException;
import ru.trainlog.common.NotFoundException;

@Service
@Transactional
public class ExerciseService {

    private final ExerciseRepository repository;
    private final CurrentAccount currentAccount;

    public ExerciseService(ExerciseRepository repository, CurrentAccount currentAccount) {
        this.repository = repository;
        this.currentAccount = currentAccount;
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> findAll() {
        return repository.findAllVisibleTo(currentAccount.id()).stream()
                .map(ExerciseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciseResponse findById(Long id) {
        return ExerciseResponse.from(get(id));
    }

    public ExerciseResponse create(ExerciseRequest request) {
        var accountId = currentAccount.id();
        var name = request.name().strip();
        if (repository.isNameTaken(name, accountId)) {
            throw nameTaken(name);
        }
        var exercise = new Exercise(accountId, name, request.muscleGroup(), request.description());
        return ExerciseResponse.from(repository.save(exercise));
    }

    public ExerciseResponse update(Long id, ExerciseRequest request) {
        var exercise = getOwn(id);
        var name = request.name().strip();
        if (repository.isNameTakenByOther(name, currentAccount.id(), id)) {
            throw nameTaken(name);
        }
        exercise.update(name, request.muscleGroup(), request.description());
        return ExerciseResponse.from(exercise);
    }

    public void delete(Long id) {
        var exercise = getOwn(id);
        if (repository.isUsedInSets(id)) {
            throw new ConflictException("Упражнение «%s» есть в подходах, его нельзя удалить".formatted(exercise.getName()));
        }
        repository.delete(exercise);
    }

    private static ConflictException nameTaken(String name) {
        return new ConflictException("Упражнение «%s» уже есть в справочнике".formatted(name));
    }

    private Exercise get(Long id) {
        return repository.findVisibleTo(id, currentAccount.id())
                .orElseThrow(() -> new NotFoundException("упражнение", id));
    }

    private Exercise getOwn(Long id) {
        var exercise = get(id);
        if (exercise.isCommon()) {
            throw new ConflictException("Упражнение «%s» из общего справочника, его нельзя менять".formatted(exercise.getName()));
        }
        return exercise;
    }
}
