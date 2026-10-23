package ru.trainlog.auth;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.trainlog.common.ConflictException;
import ru.trainlog.common.TrainlogMetrics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String VALID_BODY = """
            {"email": "me@example.com", "password": "correct horse"}
            """;

    private final AccountDetails details = new AccountDetails(new Account("me@example.com", "hash"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private TrainlogMetrics metrics;

    @Test
    void registerReturnsCreatedAccount() throws Exception {
        var request = UsernamePasswordAuthenticationToken.unauthenticated("me@example.com", "correct horse");
        when(authenticationManager.authenticate(request)).thenReturn(authenticated());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        verify(authService).register(new RegisterRequest("me@example.com", "correct horse"));
    }

    @Test
    void registerReturnsConflictForTakenEmail() throws Exception {
        doThrow(new ConflictException("Почта me@example.com уже зарегистрирована")).when(authService).register(any());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Почта me@example.com уже зарегистрирована"));

        verifyNoInteractions(authenticationManager);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void registerRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(authService, authenticationManager);
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("""
                        {"password": "correct horse"}
                        """, "Укажите почту"),
                Arguments.of("""
                        {"email": "not-an-email", "password": "correct horse"}
                        """, "Некорректный адрес почты"),
                Arguments.of("""
                        {"email": "me@%s.%s.com", "password": "correct horse"}
                        """.formatted("a".repeat(60), "b".repeat(60)), "Почта не длиннее 100 символов"),
                Arguments.of("""
                        {"email": "me@example.com"}
                        """, "Укажите пароль"),
                Arguments.of("""
                        {"email": "me@example.com", "password": "1234567"}
                        """, "Пароль от 8 до 72 символов"),
                Arguments.of("""
                        {"email": "me@example.com", "password": "%s"}
                        """.formatted("a".repeat(73)), "Пароль от 8 до 72 символов"),
                Arguments.of("{", "Некорректный формат данных")
        );
    }

    @Test
    void loginReturnsAccount() throws Exception {
        var request = UsernamePasswordAuthenticationToken.unauthenticated("me@example.com", "correct horse");
        when(authenticationManager.authenticate(request)).thenReturn(authenticated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        verifyNoInteractions(authService);
        verify(metrics).loginSucceeded();
    }

    @Test
    void loginReturnsUnauthorizedForBadCredentials() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));

        verify(metrics).loginFailed();
    }

    @ParameterizedTest
    @MethodSource("invalidLogins")
    void loginRejectsInvalidBody(String body, String detail) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(authenticationManager);
    }

    static Stream<Arguments> invalidLogins() {
        return Stream.of(
                Arguments.of("""
                        {"password": "correct horse"}
                        """, "Укажите почту"),
                Arguments.of("""
                        {"email": "  ", "password": "correct horse"}
                        """, "Укажите почту"),
                Arguments.of("""
                        {"email": "me@example.com"}
                        """, "Укажите пароль"),
                Arguments.of("""
                        {"email": "me@example.com", "password": ""}
                        """, "Укажите пароль")
        );
    }

    @Test
    void meReturnsSignedInAccount() throws Exception {
        mvc.perform(get("/api/auth/me").with(user(details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void meReturnsUnauthorizedWithoutSession() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }
}
