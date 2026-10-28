package com.swu.atch.ai.service;

import java.util.List;

public final class PredictionInputs {
    private PredictionInputs() {}

    // 경계가 모호한 문자열 연결 대신 정규화한 JSON 배열을 해시한다.
    public static String hash(AiJson json, String title, String source, String memo, int minutes) {
        return json.hash(
                List.of(AiJson.title(title), AiJson.text(source), AiJson.text(memo), minutes));
    }
}
