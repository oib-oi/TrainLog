package ru.trainlog.exercise;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.trainlog.auth.SecurityConfig;
import ru.trainlog.common.ConflictException;
import ru.trainlog.common.NotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExerciseController.class)
@Import(SecurityConfig.class)
@WithMockUser
class ExerciseControllerTest {

    private static final String VALID_BODY = """
            {"name": "Выпады", "muscleGroup": "LEGS", "description": "С гантелями"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExerciseService service;

    @Test
    void findAllReturnsExercises() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                new ExerciseResponse(1L, "Берпи", MuscleGroup.CARDIO, null, false),
                new ExerciseResponse(2L, "Выпады", MuscleGroup.LEGS, "С гантелями", false)
        ));

        mvc.perform(get("/api/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Берпи"))
                .andExpect(jsonPath("$[0].muscleGroup").value("CARDIO"))
                .andExpect(jsonPath("$[1].description").value("С гантелями"));
    }

    @Test
    void findByIdReturnsNotFound() throws Exception {
        when(service.findById(9L)).thenThrow(new NotFoundException("упражнение", 9L));

        mvc.perform(get("/api/exercises/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Не найдено: упражнение #9"));
    }

    @Test
    void createReturnsCreated() throws Exception {
        var request = new ExerciseRequest("Выпады", MuscleGroup.LEGS, "С гантелями");
        when(service.create(request)).thenReturn(new ExerciseResponse(5L, "Выпады", MuscleGroup.LEGS, "С гантелями", false));

        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Выпады"));
    }

    @Test
    void createReturnsConflictForTakenName() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Упражнение «Выпады» уже есть в справочнике"));

        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Упражнение «Выпады» уже есть в справочнике"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void createRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void updateRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(put("/api/exercises/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("""
                        {"muscleGroup": "LEGS"}
                        """, "Укажите название"),
                Arguments.of("""
                        {"name": "   ", "muscleGroup": "LEGS"}
                        """, "Укажите название"),
                Arguments.of("""
                        {"name": "%s", "muscleGroup": "LEGS"}
                        """.formatted("а".repeat(101)), "Название не длиннее 100 символов"),
                Arguments.of("""
                        {"name": "Выпады"}
                        """, "Укажите группу мышц"),
                Arguments.of("""
                        {"name": "Выпады", "muscleGroup": "LEGS", "description": "%s"}
                        """.formatted("а".repeat(501)), "Описание не длиннее 500 символов"),
                Arguments.of("""
                        {"name": "Выпады", "muscleGroup": "NECK"}
                        """, "Некорректный формат данных"),
                Arguments.of("{", "Некорректный формат данных")
        );
    }

    @Test
    void updateReturnsUpdatedExercise() throws Exception {
        var request = new ExerciseRequest("Выпады", MuscleGroup.LEGS, "С гантелями");
        when(service.update(5L, request)).thenReturn(new ExerciseResponse(5L, "Выпады", MuscleGroup.LEGS, "С гантелями", false));

        mvc.perform(put("/api/exercises/5").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.muscleGroup").value("LEGS"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/exercises/5"))
                .andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    @Test
    void deleteReturnsConflictForUsedExercise() throws Exception {
        doThrow(new ConflictException("Упражнение «Выпады» есть в подходах, его нельзя удалить"))
                .when(service).delete(5L);

        mvc.perform(delete("/api/exercises/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
