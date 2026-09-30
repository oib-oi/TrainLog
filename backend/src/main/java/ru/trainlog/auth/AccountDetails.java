package ru.trainlog.auth;

import java.util.List;

import org.springframework.security.core.userdetails.User;

public class AccountDetails extends User {

    private final Long id;

    public AccountDetails(Account account) {
        super(account.getEmail(), account.getPasswordHash(), List.of());
        this.id = account.getId();
    }

    public Long getId() {
        return id;
    }
}
