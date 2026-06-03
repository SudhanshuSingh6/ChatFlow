package com.chatflow.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link EmbeddingService} backed by any OpenAI-compatible {@code POST /embeddings}
 * endpoint (request {@code {model, input}} → response {@code {data:[{embedding:[...]}]}}).
 * The provider is selected purely by {@link EmbeddingProperties#getBaseUrl()}.
 */
@Slf4j
@Service
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private final EmbeddingProperties props;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingService(EmbeddingProperties props) {
        this.props = props;
        RestClient.Builder builder = RestClient.builder().baseUrl(props.getBaseUrl());
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + props.getApiKey());
        }
        this.restClient = builder.build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(props.getModel(), text))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no data for model " + props.getModel());
        }

        List<Double> raw = response.data().get(0).embedding();
        float[] vector = new float[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            vector[i] = raw.get(i).floatValue();
        }
        if (vector.length != props.getDimensions()) {
            log.warn("Embedding dimension {} from model {} differs from configured {}",
                    vector.length, props.getModel(), props.getDimensions());
        }
        return new EmbeddingResult(vector, props.getModel(), vector.length);
    }

    // ---- wire formats (OpenAI-compatible) ----

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(List<Data> data) {
        private record Data(List<Double> embedding) {
        }
    }
}
