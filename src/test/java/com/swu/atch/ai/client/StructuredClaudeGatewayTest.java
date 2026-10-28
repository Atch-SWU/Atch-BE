package com.swu.atch.ai.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.swu.atch.ai.dto.request.TaskDecompositionRequest;
import com.swu.atch.ai.dto.response.TimePredictionResult.ClaudeStatus;
import com.swu.atch.ai.service.AiJson;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.*;

class StructuredClaudeGatewayTest {
    private final AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final StructuredClaudeGateway gateway =
            new StructuredClaudeGateway(
                    new StaticListableBeanFactory(Map.of("client", client))
                            .getBeanProvider(AnthropicClient.class),
                    true,
                    "fixture-model",
                    new AiJson(json),
                    json);
    private final UUID id = UUID.randomUUID();

    private ClaudeGateway.Context context() {
        return new ClaudeGateway.Context(id, "작업", null, null, 10, 10, null, List.of());
    }

    private Message response(String text, StopReason stop) {
        var message = mock(Message.class);
        when(message.stopReason()).thenReturn(Optional.of(stop));
        when(message.content())
                .thenReturn(
                        List.of(
                                ContentBlock.ofText(
                                        TextBlock.builder()
                                                .text(text)
                                                .citations(List.of())
                                                .build())));
        return message;
    }

    private void respond(String text, StopReason reason) {
        var message = response(text, reason);
        when(client.messages().create(any(MessageCreateParams.class))).thenReturn(message);
    }

    @Test
    void sendsSchemaAndPreservesOnlyValidAdvice() {
        respond(
                "{\"items\":[{\"draftId\":\""
                        + id
                        + "\",\"factor\":1.1,\"reasonCodes\":[\"BASELINE_RETAINED\"]}]}",
                StopReason.END_TURN);
        assertThat(gateway.predict(List.of(context())).get(id).factor()).isEqualTo(1.1);
        verify(client.messages())
                .create(
                        argThat(
                                (MessageCreateParams p) ->
                                        p.outputConfig().orElseThrow().format().isPresent()
                                                && p.maxTokens() == 512));
    }

    @Test
    void rejectsTruncationBeforeParsingEvenValidLookingJson() {
        respond("{}", StopReason.MAX_TOKENS);
        assertThatThrownBy(() -> gateway.predict(List.of(context())))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        e -> assertThat(e.status()).isEqualTo(ClaudeStatus.TRUNCATED));
    }

    @Test
    void rejectsRefusalAndUnknownItemIdentity() {
        respond("{}", StopReason.REFUSAL);
        assertThatThrownBy(() -> gateway.predict(List.of(context())))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        e -> assertThat(e.status()).isEqualTo(ClaudeStatus.REFUSED));
        respond("{\"items\":[{\"draftId\":\"" + UUID.randomUUID() + "\"}]}", StopReason.END_TURN);
        assertThatThrownBy(() -> gateway.predict(List.of(context())))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void invalidRangeIsAnIndividualFailure() {
        respond(
                "{\"items\":[{\"draftId\":\""
                        + id
                        + "\",\"factor\":3,\"reasonCodes\":[\"BASELINE_RETAINED\"]}]}",
                StopReason.END_TURN);
        assertThat(gateway.predict(List.of(context()))).containsEntry(id, null);
    }

    @Test
    void decompositionOnlyReturnsNames() {
        respond("{\"titles\":[\"자료 수집\",\"초안 작성\"]}", StopReason.END_TURN);
        assertThat(
                        gateway.decompose(
                                new TaskDecompositionRequest(
                                        id,
                                        "기획",
                                        null,
                                        LocalDate.of(2026, 10, 29),
                                        null,
                                        LocalDate.of(2026, 10, 29),
                                        null,
                                        true,
                                        null)))
                .containsExactly("자료 수집", "초안 작성");
    }

    @Test
    void distinguishesTimeoutFromNetworkFailure() {
        assertThat(AiProviderException.ioStatus(new java.net.SocketTimeoutException()))
                .isEqualTo(ClaudeStatus.TIMEOUT);
        assertThat(AiProviderException.ioStatus(new java.net.UnknownHostException()))
                .isEqualTo(ClaudeStatus.UNAVAILABLE);
    }
}
