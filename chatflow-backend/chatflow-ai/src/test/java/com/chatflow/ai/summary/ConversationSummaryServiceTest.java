package com.chatflow.ai.summary;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.rag.ConversationAccessClient;
import com.chatflow.contracts.dto.ConversationTranscript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock ConversationAccessClient accessClient;
    @Mock TranscriptClient transcriptClient;
    @Mock ChatCompletionService chatCompletionService;
    @InjectMocks ConversationSummaryService service;

    private final UUID caller = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();

    @Test
    void rejectsNonParticipants() {
        when(accessClient.isParticipant(conversation, caller)).thenReturn(false);

        assertThatThrownBy(() -> service.summarizeUnread(caller, conversation))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(transcriptClient, chatCompletionService);
    }

    @Test
    void returnsCaughtUpWhenNoUnread() {
        when(accessClient.isParticipant(conversation, caller)).thenReturn(true);
        when(transcriptClient.fetchUnread(conversation, caller))
                .thenReturn(new ConversationTranscript(0, 42, 42, List.of()));

        SummaryResponse response = service.summarizeUnread(caller, conversation);

        assertThat(response.messageCount()).isZero();
        assertThat(response.summary()).contains("caught up");
        verifyNoInteractions(chatCompletionService);
    }

    @Test
    void summarizesUnreadTranscript() {
        when(accessClient.isParticipant(conversation, caller)).thenReturn(true);
        when(transcriptClient.fetchUnread(conversation, caller))
                .thenReturn(new ConversationTranscript(2, 10, 11, List.of(
                        new ConversationTranscript.Entry("alice", "ship friday?"),
                        new ConversationTranscript.Entry("bob", "yes, friday"))));
        when(chatCompletionService.complete(anyString(), contains("alice: ship friday?\nbob: yes, friday"), anyString()))
                .thenReturn("Alice and Bob agreed to ship Friday.");

        SummaryResponse response = service.summarizeUnread(caller, conversation);

        assertThat(response.summary()).contains("ship Friday");
        assertThat(response.messageCount()).isEqualTo(2);
        assertThat(response.fromSequence()).isEqualTo(10);
        assertThat(response.toSequence()).isEqualTo(11);
    }
}
