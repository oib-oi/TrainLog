package ru.trainlog.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ru.trainlog.common.ConflictException;
import ru.trainlog.common.TrainlogMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TrainlogMetrics metrics;

    @InjectMocks
    private AuthService service;

    @Test
    void registerSavesNormalizedEmailAndPasswordHash() {
        when(passwordEncoder.encode("correct horse")).thenReturn("hash");

        service.register(new RegisterRequest(" New@Example.com ", "correct horse"));

        var saved = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        verify(metrics).registered();
    }

    @Test
    void registerRejectsTakenEmail() {
        when(repository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("NEW@example.com", "correct horse")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Почта new@example.com уже зарегистрирована");
        verify(repository, never()).save(any());
        verifyNoInteractions(metrics);
    }
}
