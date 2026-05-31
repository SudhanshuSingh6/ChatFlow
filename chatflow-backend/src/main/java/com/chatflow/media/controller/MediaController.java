package com.chatflow.media.controller;

import com.chatflow.media.dto.MediaMessageResponse;
import com.chatflow.media.dto.MediaUploadRequest;
import com.chatflow.media.dto.MediaUrlResponse;
import com.chatflow.media.entity.MessageType;
import com.chatflow.media.service.MediaAccessService;
import com.chatflow.media.service.MediaCleanupService;
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
    private final MediaCleanupService mediaCleanupService;
    private final MediaAccessService mediaAccessService;


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

    @GetMapping("/{id}/url")
    public MediaUrlResponse getSignedUrl(@PathVariable UUID id, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return mediaAccessService.getSignedUrl(callerId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        mediaCleanupService.deleteMedia(callerId, id);
    }
}