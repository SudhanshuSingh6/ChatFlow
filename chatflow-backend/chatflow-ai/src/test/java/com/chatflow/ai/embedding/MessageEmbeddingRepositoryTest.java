package com.chatflow.ai.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageEmbeddingRepositoryTest {

    @Test
    void rendersPgvectorLiteral() {
        assertThat(MessageEmbeddingRepository.toVectorLiteral(new float[]{1.0f, -2.5f, 0.0f}))
                .isEqualTo("[1.0,-2.5,0.0]");
    }

    @Test
    void rendersEmptyVector() {
        assertThat(MessageEmbeddingRepository.toVectorLiteral(new float[]{})).isEqualTo("[]");
    }
}
