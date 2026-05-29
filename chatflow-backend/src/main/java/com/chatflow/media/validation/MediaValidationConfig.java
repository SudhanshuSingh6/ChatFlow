package com.chatflow.media.validation;

import com.chatflow.media.entity.MessageType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.media")
public class MediaValidationConfig {

    private Map<MessageType, Long> maxFileSizeBytes = Map.of(
            MessageType.IMAGE, 10L  * 1024 * 1024,   // 10 MB
            MessageType.VIDEO, 100L * 1024 * 1024,   // 100 MB
            MessageType.AUDIO, 20L  * 1024 * 1024,   // 20 MB
            MessageType.FILE,  50L  * 1024 * 1024    // 50 MB
    );

    private Map<MessageType, List<String>> allowedMimeTypes = Map.of(
            MessageType.IMAGE, List.of("image/jpeg", "image/png", "image/gif",
                    "image/webp", "image/heic"),
            MessageType.VIDEO, List.of("video/mp4", "video/quicktime",
                    "video/webm", "video/x-msvideo"),
            MessageType.AUDIO, List.of("audio/mpeg", "audio/ogg", "audio/wav",
                    "audio/aac", "audio/mp4"),
            MessageType.FILE,  List.of("application/pdf", "application/zip",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/plain", "text/csv")
    );

    private List<String> blockedExtensions = List.of(
            ".exe", ".bat", ".cmd", ".sh", ".bash", ".zsh",
            ".ps1", ".psm1", ".php", ".php3", ".php4", ".php5",
            ".phtml", ".asp", ".aspx", ".jsp", ".jspx",
            ".py", ".rb", ".pl", ".cgi", ".htaccess",
            ".jar", ".class", ".war", ".ear", ".dll", ".so"
    );
}