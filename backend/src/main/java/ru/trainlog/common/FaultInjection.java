package ru.trainlog.common;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@Endpoint(id = "faults")
public class FaultInjection implements HandlerInterceptor, WebMvcConfigurer {

    private volatile double errorRate;
    private volatile long delayMs;

    @ReadOperation
    public Map<String, Object> settings() {
        return Map.of("errorRate", errorRate, "delayMs", delayMs);
    }

    @WriteOperation
    public Map<String, Object> update(@Nullable Double errorRate, @Nullable Long delayMs) {
        if (errorRate != null) {
            this.errorRate = Math.clamp(errorRate, 0.0, 1.0);
        }
        if (delayMs != null) {
            this.delayMs = Math.max(delayMs, 0);
        }
        return settings();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        if (errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Искусственный сбой");
        }
        return true;
    }
}
