package ru.trainlog.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {

    public Long id() {
        var details = (AccountDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getId();
    }
}
