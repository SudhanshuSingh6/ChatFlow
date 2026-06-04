package com.chatflow.ai.rag;

/** A natural-language question to ask over a conversation's history (RAG). */
public record AskRequest(String question) {
}
