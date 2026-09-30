package ru.trainlog.workout;

import java.math.BigDecimal;
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
import ru.trainlog.common.NotFoundException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkoutSetController.class)
@Import(SecurityConfig.class)
@WithMockUser
class WorkoutSetControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkoutSetService service;

    @Test
    void updateReturnsUpdatedSet() throws Exception {
        var request = new WorkoutSetRequest(2L, 12, new BigDecimal("42.25"));
        when(service.update(10L, request))
                .thenReturn(new WorkoutSetResponse(10L, 1L, 2L, "Приседания", 12, new BigDecimal("42.25")));

        mvc.perform(put("/api/sets/10").contentType(MediaType.APPLICATION_JSON).content("""
                        {"exerciseId": 2, "reps": 12, "weightKg": 42.25}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reps").value(12))
                .andExpect(jsonPath("$.weightKg").value(42.25));
    }

    @Test
    void updateAcceptsSetWithoutWeight() throws Exception {
        when(service.update(10L, new WorkoutSetRequest(2L, 12, null)))
                .thenReturn(new WorkoutSetResponse(10L, 1L, 2L, "Подтягивания", 12, null));

        mvc.perform(put("/api/sets/10").contentType(MediaType.APPLICATION_JSON).content("""
                        {"exerciseId": 2, "reps": 12}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidSets")
    void updateRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(put("/api/sets/10").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidSets() {
        return Stream.of(
                Arguments.of("""
                        {"reps": 10}
                        """, "Выберите упражнение"),
                Arguments.of("""
                        {"exerciseId": 2}
                        """, "Укажите число повторений"),
                Arguments.of("""
                        {"exerciseId": 2, "reps": 0}
                        """, "Число повторений должно быть больше нуля"),
                Arguments.of("""
                        {"exerciseId": 2, "reps": 10, "weightKg": -1}
                        """, "Вес не может быть отрицательным"),
                Arguments.of("""
                        {"exerciseId": 2, "reps": 10, "weightKg": 10000}
                        """, "Вес не больше 9999,99 кг с точностью до сотых"),
                Arguments.of("""
                        {"exerciseId": 2, "reps": 10, "weightKg": 50.125}
                        """, "Вес не больше 9999,99 кг с точностью до сотых"),
                Arguments.of("""
                        {"exerciseId": 2, "reps": "много"}
                        """, "Некорректный формат данных")
        );
    }

    @Test
    void updateReturnsNotFound() throws Exception {
        when(service.update(10L, new WorkoutSetRequest(2L, 12, null)))
                .thenThrow(new NotFoundException("подход", 10L));

        mvc.perform(put("/api/sets/10").contentType(MediaType.APPLICATION_JSON).content("""
                        {"exerciseId": 2, "reps": 12}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Не найдено: подход #10"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/sets/10"))
                .andExpect(status().isNoContent());

        verify(service).delete(10L);
    }

    @Test
    void deleteReturnsNotFound() throws Exception {
        doThrow(new NotFoundException("подход", 10L)).when(service).delete(10L);

        mvc.perform(delete("/api/sets/10"))
                .andExpect(status().isNotFound());
    }
}
