package com.chatflow.conversation.service;

import com.chatflow.conversation.search.MessageEmbeddingRepository;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DailyCleanupServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationParticipantRepository participantRepository =
            mock(ConversationParticipantRepository.class);
    private final MessageEmbeddingRepository embeddingRepository = mock(MessageEmbeddingRepository.class);
    // A bare mock manager makes TransactionTemplate run callbacks inline (getTransaction → null).
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private final DailyCleanupService service = new DailyCleanupService(
            messageRepository, notificationRepository, conversationRepository,
            participantRepository, embeddingRepository, txManager);

    {
        ReflectionTestUtils.setField(service, "messageRetentionDays", 30L);
        ReflectionTestUtils.setField(service, "notificationRetentionDays", 7L);
        ReflectionTestUtils.setField(service, "groupRetentionDays", 30L);
    }

    @Test
    void purgeMessagesUsesRetentionCutoff() {
        Instant expected = Instant.now().minus(30, ChronoUnit.DAYS);

        service.purgeMessages();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).purgeDeletedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(expected.minusSeconds(10), expected.plusSeconds(10));
    }

    @Test
    void purgeNotificationsUsesItsOwnRetentionCutoff() {
        Instant expected = Instant.now().minus(7, ChronoUnit.DAYS);

        service.purgeNotifications();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).purgeDeletedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(expected.minusSeconds(10), expected.plusSeconds(10));
    }

    @Test
    void dailyCleanupRunsAllThreeSteps() {
        when(conversationRepository.findGroupIdsToPurge(any())).thenReturn(List.of());

        service.dailyCleanup();

        verify(messageRepository).purgeDeletedBefore(any());
        verify(notificationRepository).purgeDeletedBefore(any());
        verify(conversationRepository).findGroupIdsToPurge(any());
    }

    @Test
    void purgeGroupsCascadesEachGroupInOrder() {
        UUID groupId = UUID.randomUUID();
        when(conversationRepository.findGroupIdsToPurge(any())).thenReturn(List.of(groupId));

        service.purgeGroups();

        InOrder inOrder = inOrder(embeddingRepository, notificationRepository, messageRepository,
                participantRepository, conversationRepository);
        // Embeddings must be deleted before messages (FK on message_embeddings.message_id).
        inOrder.verify(embeddingRepository).deleteForConversation(groupId);
        inOrder.verify(notificationRepository).deleteByConversation(groupId);
        inOrder.verify(messageRepository).deleteByConversationId(groupId);
        inOrder.verify(participantRepository).deleteByConversationId(groupId);
        inOrder.verify(conversationRepository).deleteById(groupId);
    }

    @Test
    void purgeGroupsContinuesAfterOneGroupFails() {
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(conversationRepository.findGroupIdsToPurge(any())).thenReturn(List.of(failing, ok));
        doThrow(new RuntimeException("boom")).when(conversationRepository).deleteById(failing);

        service.purgeGroups();

        // The failure on the first group must not stop the second from being purged.
        verify(conversationRepository).deleteById(ok);
        verify(messageRepository).deleteByConversationId(ok);
    }
}
