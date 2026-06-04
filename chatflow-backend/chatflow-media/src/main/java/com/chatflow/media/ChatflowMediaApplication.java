package com.chatflow.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Media processing worker (Phase 2). Consumes media-processing events, generates thumbnails
 * off the chat hot path, and reports completion via events. Stateless — message creation and
 * media metadata remain in core.
 */
@SpringBootApplication
public class ChatflowMediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatflowMediaApplication.class, args);
    }
}
