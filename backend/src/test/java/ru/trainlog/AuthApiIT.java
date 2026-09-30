package ru.trainlog;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.TestSecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIT extends IntegrationTest {

    private static final String CREDENTIALS = """
            {"email": "new@example.com", "password": "correct horse"}
            """;

    @BeforeEach
    void forgetAccount() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/workouts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/exercises")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void keepsHealthCheckOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void registersAndSignsIn() throws Exception {
        var session = register();

        var saved = accounts.findByEmail("new@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct horse");
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void keepsWorkoutsOfSignedInAccountPrivate() throws Exception {
        var session = register();

        mvc.perform(post("/api/workouts").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                        {"performedOn": "2026-09-29", "title": "Ноги"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/workouts").cookie(session))
                .andExpect(jsonPath("$.totalElements").value(1));
        signIn(account);
        mvc.perform(get("/api/workouts"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void logsInIgnoringEmailCase() throws Exception {
        register();

        var session = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": " New@Example.com ", "password": "correct horse"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void changesSessionIdOnLogin() throws Exception {
        var before = register();

        var after = mvc.perform(post("/api/auth/login").cookie(before).contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mvc.perform(get("/api/auth/me").cookie(after)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(before)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"new@example.com, wrong horse", "nobody@example.com, correct horse"})
    void rejectsWrongCredentials(String email, String password) throws Exception {
        register();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));
    }

    @Test
    void rejectsTakenEmail() throws Exception {
        register();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "NEW@example.com", "password": "another password"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Почта new@example.com уже зарегистрирована"));
    }

    @ParameterizedTest
    @CsvSource({
            "not-an-email, correct horse, Некорректный адрес почты",
            "new@example.com, short, Пароль от 8 до 72 символов"
    })
    void rejectsInvalidRegistration(String email, String password, String detail) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void logsOut() throws Exception {
        var session = register();

        mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    private Cookie register() throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("SESSION");
    }
}
