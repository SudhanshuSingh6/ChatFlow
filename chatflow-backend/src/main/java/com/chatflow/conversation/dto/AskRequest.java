package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A natural-language question to ask over a conversation's history (RAG). */
public record AskRequest(
        @NotBlank @Size(max = 1000) String question
) {
}
