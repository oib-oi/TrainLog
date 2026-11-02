package ru.trainlog.common;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaultInjectionTest {

    private final FaultInjection faults = new FaultInjection();

    @Test
    void disabledByDefault() throws Exception {
        assertThat(faults.settings()).isEqualTo(Map.of("errorRate", 0.0, "delayMs", 0L));
        assertThat(faults.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void updateClampsValuesAndKeepsOmittedOnes() {
        faults.update(1.5, -10L);
        assertThat(faults.settings()).isEqualTo(Map.of("errorRate", 1.0, "delayMs", 0L));

        faults.update(null, 200L);
        assertThat(faults.settings()).isEqualTo(Map.of("errorRate", 1.0, "delayMs", 200L));
    }

    @Test
    void failsEveryRequestWhenErrorRateIsOne() {
        faults.update(1.0, null);

        assertThatThrownBy(() -> faults.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }
}
