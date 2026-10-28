package com.swu.atch.ai.client;

import com.swu.atch.ai.domain.PredictionReasonCode;
import com.swu.atch.ai.dto.request.TaskDecompositionRequest;
import com.swu.atch.ai.dto.response.*;

import java.util.*;

public interface ClaudeGateway {
    record Context(
            UUID draftId,
            String title,
            String sourceTaskName,
            String memo,
            int userMinutes,
            double baseline,
            TimeEstimationProfileResponse profile,
            List<SimilarTodoSummary> similarTodos) {}

    record Advice(double factor, List<PredictionReasonCode> reasonCodes) {}

    boolean enabled();

    String model();

    Map<UUID, Advice> predict(List<Context> contexts);

    List<String> decompose(TaskDecompositionRequest request);
}
