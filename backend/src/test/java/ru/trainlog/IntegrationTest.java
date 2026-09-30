package ru.trainlog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.trainlog.auth.Account;
import ru.trainlog.auth.AccountDetails;
import ru.trainlog.auth.AccountRepository;
import ru.trainlog.exercise.ExerciseRepository;
import ru.trainlog.workout.WorkoutRepository;
import ru.trainlog.workout.WorkoutSetRepository;

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:../db/migration")
                .load()
                .migrate();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected ExerciseRepository exercises;

    @Autowired
    protected WorkoutRepository workouts;

    @Autowired
    protected WorkoutSetRepository sets;

    protected Account account;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void prepareDatabase() {
        sets.deleteAllInBatch();
        workouts.deleteAllInBatch();
        exercises.deleteAllInBatch();
        accounts.deleteAllInBatch();
        account = accounts.save(new Account("me@example.com", "hash"));
        signIn(account);
    }

    @AfterEach
    void signOut() {
        TestSecurityContextHolder.clearContext();
    }

    protected void signIn(Account account) {
        var details = new AccountDetails(account);
        TestSecurityContextHolder.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
    }
}
