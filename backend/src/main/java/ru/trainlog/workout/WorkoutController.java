package ru.trainlog.workout;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.trainlog.common.PageResponse;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final WorkoutSetService setService;

    public WorkoutController(WorkoutService workoutService, WorkoutSetService setService) {
        this.workoutService = workoutService;
        this.setService = setService;
    }

    @GetMapping
    public PageResponse<WorkoutResponse> findAll(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Номер страницы не может быть отрицательным") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Размер страницы от 1 до 100")
            @Max(value = 100, message = "Размер страницы от 1 до 100") int size) {
        return workoutService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public WorkoutResponse findById(@PathVariable Long id) {
        return workoutService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse create(@Valid @RequestBody WorkoutRequest request) {
        return workoutService.create(request);
    }

    @PutMapping("/{id}")
    public WorkoutResponse update(@PathVariable Long id, @Valid @RequestBody WorkoutRequest request) {
        return workoutService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workoutService.delete(id);
    }

    @GetMapping("/{id}/sets")
    public List<WorkoutSetResponse> findSets(@PathVariable Long id) {
        return setService.findByWorkout(id);
    }

    @PostMapping("/{id}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutSetResponse createSet(@PathVariable Long id, @Valid @RequestBody WorkoutSetRequest request) {
        return setService.create(id, request);
    }
}
