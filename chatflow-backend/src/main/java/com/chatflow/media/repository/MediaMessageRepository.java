package com.chatflow.media.repository;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaMessageRepository extends JpaRepository<MediaMessage, UUID> {

    List<MediaMessage> findByConversationIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID conversationId, Pageable pageable);

    List<MediaMessage> findByGroupIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID groupId, Pageable pageable);

    Optional<MediaMessage> findByIdAndDeletedFalse(UUID id);

    @Modifying
    @Query("UPDATE MediaMessage m SET m.status = :status, m.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE m.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") MediaStatus status);

    @Modifying
    @Query("UPDATE MediaMessage m SET m.mediaUrl = :url, m.status = :status, " +
            "m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int updateMediaUrl(@Param("id") UUID id,
                       @Param("url") String url,
                       @Param("status") MediaStatus status);

    @Modifying
    @Query("UPDATE MediaMessage m SET m.thumbnailUrl = :url, " +
            "m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int updateThumbnailUrl(@Param("id") UUID id, @Param("url") String url);

    @Query("SELECT m FROM MediaMessage m WHERE m.status = 'UPLOADING' " +
            "AND m.createdAt < :cutoff")
    List<MediaMessage> findStaleUploads(
            @Param("cutoff") java.time.LocalDateTime cutoff);
}