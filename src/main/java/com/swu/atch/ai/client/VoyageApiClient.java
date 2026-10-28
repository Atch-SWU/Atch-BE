package com.swu.atch.ai.client;

import com.swu.atch.ai.dto.response.TimePredictionResult.ClaudeStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Component
public class VoyageApiClient implements VoyageGateway {
    private final boolean enabled;
    private final String key, model;
    private final RestClient client;
    private final JsonMapper json;

    public VoyageApiClient(
            @Value("${app.ai.voyage.enabled:false}") boolean enabled,
            @Value("${app.ai.voyage.api-key:}") String key,
            @Value("${app.ai.voyage.model:voyage-4-lite}") String model,
            @Value("${app.ai.voyage.timeout:PT5S}") Duration timeout,
            JsonMapper json) {
        this.enabled = enabled;
        this.key = key;
        this.model = model;
        this.json = json;
        if (enabled && key.isBlank()) throw new IllegalStateException("Voyage API 키가 필요합니다.");
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        client =
                RestClient.builder()
                        .baseUrl("https://api.voyageai.com")
                        .requestFactory(factory)
                        .build();
    }

    public boolean enabled() {
        return enabled;
    }

    public String model() {
        return model;
    }

    public List<float[]> embed(List<String> texts, String inputType) {
        if (!enabled) throw new AiProviderException(ClaudeStatus.DISABLED);
        if (texts.isEmpty()
                || texts.size() > 20
                || !Set.of("query", "document").contains(inputType))
            throw new IllegalArgumentException("임베딩 요청 범위 오류");
        try {
            String body =
                    client.post()
                            .uri("/v1/embeddings")
                            .header("Authorization", "Bearer " + key)
                            .body(
                                    Map.of(
                                            "input",
                                            texts,
                                            "model",
                                            model,
                                            "input_type",
                                            inputType,
                                            "output_dimension",
                                            1024,
                                            "truncation",
                                            false))
                            .retrieve()
                            .body(String.class);
            var data = json.readTree(body).path("data");
            if (!data.isArray() || data.size() != texts.size())
                throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
            var result = new ArrayList<float[]>(Collections.nCopies(texts.size(), null));
            for (var item : data) {
                int index = item.path("index").asInt(-1);
                var vector = item.path("embedding");
                if (index < 0
                        || index >= texts.size()
                        || result.get(index) != null
                        || !vector.isArray()
                        || vector.size() != 1024)
                    throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
                float[] values = new float[1024];
                double norm = 0;
                for (int i = 0; i < 1024; i++) {
                    if (!vector.get(i).isNumber())
                        throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
                    values[i] = (float) vector.get(i).asDouble();
                    if (!Float.isFinite(values[i]))
                        throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
                    norm += (double) values[i] * values[i];
                }
                if (norm == 0) throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
                result.set(index, values);
            }
            return result;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            throw new AiProviderException(AiProviderException.ioStatus(ex));
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
            throw new AiProviderException(ClaudeStatus.RATE_LIMITED);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw new AiProviderException(ClaudeStatus.UNAVAILABLE);
        } catch (RuntimeException ex) {
            throw new AiProviderException(ClaudeStatus.INVALID_RESPONSE);
        }
    }
}
