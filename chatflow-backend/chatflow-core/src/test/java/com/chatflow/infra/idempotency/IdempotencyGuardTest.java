package com.chatflow.infra.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyGuardTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdempotencyGuard guard = new IdempotencyGuard(jdbc);

    @Test
    void firstTimeIsTrueWhenRowInserted() {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        assertThat(guard.firstTime("g", UUID.randomUUID())).isTrue();
    }

    @Test
    void firstTimeIsFalseWhenConflict() {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        assertThat(guard.firstTime("g", UUID.randomUUID())).isFalse();
    }

    @Test
    void alreadyProcessedReflectsCount() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
        assertThat(guard.alreadyProcessed("g", UUID.randomUUID())).isTrue();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
        assertThat(guard.alreadyProcessed("g", UUID.randomUUID())).isFalse();
    }
}
