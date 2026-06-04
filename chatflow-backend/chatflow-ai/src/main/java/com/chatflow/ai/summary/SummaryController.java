package com.chatflow.ai.summary;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * "Catch me up" endpoint. Caller is taken from the validated JWT ({@code principal.getName()}
 * → user id); membership is enforced in the service via core.
 */
@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class SummaryController {

    private final ConversationSummaryService summaryService;

    @GetMapping("/{conversationId}/summary")
    public SummaryResponse summary(@PathVariable UUID conversationId, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return summaryService.summarizeUnread(callerId, conversationId);
    }
}
