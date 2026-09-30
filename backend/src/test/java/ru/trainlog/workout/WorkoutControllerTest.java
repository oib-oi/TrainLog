package ru.trainlog.workout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.trainlog.auth.SecurityConfig;
import ru.trainlog.common.NotFoundException;
import ru.trainlog.common.PageResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkoutController.class)
@Import(SecurityConfig.class)
@WithMockUser
class WorkoutControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkoutService workoutService;

    @MockitoBean
    private WorkoutSetService setService;

    @Test
    void findAllReturnsWorkouts() throws Exception {
        when(workoutService.findAll(0, 20)).thenReturn(
                new PageResponse<>(List.of(new WorkoutResponse(1L, DATE, "Ноги", null, 60)), 0, 20, 1, 1));

        mvc.perform(get("/api/workouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].performedOn").value("2026-09-29"))
                .andExpect(jsonPath("$.content[0].title").value("Ноги"))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findAllPassesPageAndSize() throws Exception {
        when(workoutService.findAll(2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 7, 2));

        mvc.perform(get("/api/workouts").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20, Номер страницы не может быть отрицательным",
            "0, 0, Размер страницы от 1 до 100",
            "0, 101, Размер страницы от 1 до 100"
    })
    void findAllRejectsInvalidPaging(String page, String size, String detail) throws Exception {
        mvc.perform(get("/api/workouts").param("page", page).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(workoutService);
    }

    @Test
    void findByIdReturnsNotFound() throws Exception {
        when(workoutService.findById(4L)).thenThrow(new NotFoundException("тренировка", 4L));

        mvc.perform(get("/api/workouts/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Не найдено: тренировка #4"));
    }

    @Test
    void createReturnsCreated() throws Exception {
        var request = new WorkoutRequest(DATE, "Ноги", "Тяжело", 60);
        when(workoutService.create(request)).thenReturn(new WorkoutResponse(1L, DATE, "Ноги", "Тяжело", 60));

        mvc.perform(post("/api/workouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-29", "title": "Ноги", "notes": "Тяжело", "durationMinutes": 60}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.notes").value("Тяжело"));
    }

    @Test
    void createAcceptsWorkoutWithoutOptionalFields() throws Exception {
        var request = new WorkoutRequest(DATE, "Ноги", null, null);
        when(workoutService.create(request)).thenReturn(new WorkoutResponse(1L, DATE, "Ноги", null, null));

        mvc.perform(post("/api/workouts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-29", "title": "Ноги"}
                        """))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @MethodSource("invalidWorkouts")
    void createRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(post("/api/workouts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(workoutService);
    }

    @ParameterizedTest
    @MethodSource("invalidWorkouts")
    void updateRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(put("/api/workouts/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(workoutService);
    }

    static Stream<Arguments> invalidWorkouts() {
        return Stream.of(
                Arguments.of("""
                        {"title": "Ноги"}
                        """, "Укажите дату"),
                Arguments.of("""
                        {"performedOn": "29.09.2026", "title": "Ноги"}
                        """, "Некорректный формат данных"),
                Arguments.of("""
                        {"performedOn": "2026-09-29", "title": " "}
                        """, "Укажите название"),
                Arguments.of("""
                        {"performedOn": "2026-09-29", "title": "%s"}
                        """.formatted("а".repeat(101)), "Название не длиннее 100 символов"),
                Arguments.of("""
                        {"performedOn": "2026-09-29", "title": "Ноги", "notes": "%s"}
                        """.formatted("а".repeat(501)), "Заметки не длиннее 500 символов"),
                Arguments.of("""
                        {"performedOn": "2026-09-29", "title": "Ноги", "durationMinutes": 0}
                        """, "Длительность должна быть больше нуля")
        );
    }

    @Test
    void updateReturnsUpdatedWorkout() throws Exception {
        var request = new WorkoutRequest(DATE, "Спина", null, 45);
        when(workoutService.update(1L, request)).thenReturn(new WorkoutResponse(1L, DATE, "Спина", null, 45));

        mvc.perform(put("/api/workouts/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-29", "title": "Спина", "durationMinutes": 45}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Спина"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/workouts/1"))
                .andExpect(status().isNoContent());

        verify(workoutService).delete(1L);
    }

    @Test
    void findSetsReturnsSetsOfWorkout() throws Exception {
        when(setService.findByWorkout(1L)).thenReturn(List.of(
                new WorkoutSetResponse(10L, 1L, 2L, "Приседания", 8, new BigDecimal("100.50"))
        ));

        mvc.perform(get("/api/workouts/1/sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].workoutId").value(1))
                .andExpect(jsonPath("$[0].exerciseId").value(2))
                .andExpect(jsonPath("$[0].exerciseName").value("Приседания"))
                .andExpect(jsonPath("$[0].reps").value(8))
                .andExpect(jsonPath("$[0].weightKg").value(100.5));
    }

    @Test
    void createSetReturnsCreated() throws Exception {
        var request = new WorkoutSetRequest(2L, 8, new BigDecimal("100.5"));
        when(setService.create(1L, request))
                .thenReturn(new WorkoutSetResponse(10L, 1L, 2L, "Приседания", 8, new BigDecimal("100.5")));

        mvc.perform(post("/api/workouts/1/sets").contentType(MediaType.APPLICATION_JSON).content("""
                        {"exerciseId": 2, "reps": 8, "weightKg": 100.5}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void createSetReturnsNotFoundForMissingWorkout() throws Exception {
        when(setService.create(1L, new WorkoutSetRequest(2L, 8, null)))
                .thenThrow(new NotFoundException("тренировка", 1L));

        mvc.perform(post("/api/workouts/1/sets").contentType(MediaType.APPLICATION_JSON).content("""
                        {"exerciseId": 2, "reps": 8}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Не найдено: тренировка #1"));
    }

    @ParameterizedTest
    @MethodSource("ru.trainlog.workout.WorkoutSetControllerTest#invalidSets")
    void createSetRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(post("/api/workouts/1/sets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(setService);
    }
}
