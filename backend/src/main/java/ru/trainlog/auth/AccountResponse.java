package ru.trainlog.auth;

public record AccountResponse(Long id, String email) {

    static AccountResponse from(AccountDetails details) {
        return new AccountResponse(details.getId(), details.getUsername());
    }
}
