package com.chatflow.infra.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side-effect only after the current transaction commits — so real-time
 * pushes are never sent for data that later rolls back. If there is no active
 * transaction (e.g. in a unit test), the action runs immediately.
 *
 * <p>This is the correct pattern for WebSocket fan-out from a {@code @Transactional}
 * service: persist first, deliver after commit.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }
}
