package com.chatflow.media.controller;

import com.chatflow.media.dto.MediaMessageResponse;
import com.chatflow.media.dto.MediaUploadRequest;
import com.chatflow.media.entity.MessageType;
import com.chatflow.media.service.MediaMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaMessageService mediaMessageService;

    /**
     * Multipart upload — file + metadata in one request.
     *
     * Request:
     *   POST /api/messages/media?type=IMAGE
     *   Content-Type: multipart/form-data
     *   Parts: file (required), chatId or groupId (one required), caption (optional)
     *
     * Returns 202 ACCEPTED — the message is persisted with status=UPLOADING.
     * Storage and delivery happen in Phase 3/4. The client polls getById()
     * or receives a WebSocket event when status transitions to READY.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MediaMessageResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") MessageType messageType,
            @Valid @ModelAttribute MediaUploadRequest request,
            Principal principal) {

        UUID senderId = UUID.fromString(principal.getName());
        return mediaMessageService.upload(senderId, file, messageType, request);
    }

    @GetMapping("/{id}")
    public MediaMessageResponse getById(@PathVariable UUID id, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return mediaMessageService.getById(callerId, id);
    }
}