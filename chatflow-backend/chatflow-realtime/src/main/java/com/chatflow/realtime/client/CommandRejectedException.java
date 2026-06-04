package com.chatflow.realtime.client;

/**
 * An inbound command core refused (validation/authorization 4xx) or that couldn't be delivered.
 * Carries the client-facing message the socket layer puts into an ERROR frame. Configured as an
 * <em>ignored</em> exception on the circuit breaker so per-user validation errors don't trip it.
 */
public class CommandRejectedException extends RuntimeException {
    public CommandRejectedException(String message) {
        super(message);
    }
}
