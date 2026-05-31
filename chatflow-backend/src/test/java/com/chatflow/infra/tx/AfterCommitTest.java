package com.chatflow.infra.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsImmediatelyWhenNoTransactionActive() {
        AtomicBoolean ran = new AtomicBoolean(false);

        AfterCommit.run(() -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void defersUntilCommitWhenSynchronizationActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean ran = new AtomicBoolean(false);

        AfterCommit.run(() -> ran.set(true));

        // Registered, but not yet executed (no commit has happened).
        assertThat(ran).isFalse();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        // Simulate the commit callback.
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        assertThat(ran).isTrue();
    }
}
