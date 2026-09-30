package ru.trainlog.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterRequest body,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        authService.register(body);
        return signIn(body.email(), body.password(), request, response);
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody LoginRequest body,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        return signIn(body.email(), body.password(), request, response);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountDetails details) {
        return AccountResponse.from(details);
    }

    private AccountResponse signIn(String email, String password,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return AccountResponse.from((AccountDetails) authentication.getPrincipal());
    }
}
