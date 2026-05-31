package com.chatflow.conversation.controller;

import com.chatflow.conversation.dto.SearchPageResponse;
import com.chatflow.conversation.service.MessageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageSearchController {

    private final MessageSearchService messageSearchService;

    @GetMapping("/search")
    public SearchPageResponse search(
            @RequestParam String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return messageSearchService.search(callerId, query, cursor, limit);
    }
}
