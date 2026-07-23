package org.kabieror.elwasys.backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Deterministischer Unit-Test (Mockito, keine DB) für {@link ExpiredExecutionsHealthIndicator}
 * (Issue #32 - Betriebskonzept Dauerbetrieb): keine offene abgelaufene Ausführung -> {@code UP},
 * mindestens eine -> {@link Status#OUT_OF_SERVICE} mit Detail {@code count}.
 */
class ExpiredExecutionsHealthIndicatorTest {

    private final ExecutionService executionService = mock(ExecutionService.class);

    private final ExpiredExecutionsHealthIndicator indicator =
            new ExpiredExecutionsHealthIndicator(this.executionService);

    @Test
    void noExpiredExecutionsIsUp() {
        when(this.executionService.getAllExpiredExecutions()).thenReturn(List.of());

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("count", 0);
    }

    @Test
    void openExpiredExecutionsAreOutOfServiceWithCount() {
        when(this.executionService.getAllExpiredExecutions())
                .thenReturn(List.of(mock(ExecutionEntity.class), mock(ExecutionEntity.class)));

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("count", 2);
    }
}
