package ru.trainlog.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.trainlog.common.ConflictException;

@Service
@Transactional
public class AuthService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(RegisterRequest request) {
        var email = Account.normalizeEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictException("Почта %s уже зарегистрирована".formatted(email));
        }
        repository.save(new Account(email, passwordEncoder.encode(request.password())));
    }
}
