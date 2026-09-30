package ru.trainlog.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService implements UserDetailsService {

    private final AccountRepository repository;

    public AccountDetailsService(AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return repository.findByEmail(Account.normalizeEmail(email))
                .map(AccountDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }
}
