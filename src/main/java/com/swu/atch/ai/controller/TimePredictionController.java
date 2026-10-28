package com.swu.atch.ai.controller;

import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.swu.atch.ai.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;

// 본인 사용자 ID와 현재 비밀번호 상태를 전달하고, 실제 처리·트랜잭션은 Service에 맡긴다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "10. [프론트] AI 시간 예측", description = "AI 초안 발급 → 사용자 예상시간 입력 → 시간 보정 제안 → 투두 저장 순서입니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태는 Service에서 검증", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증·AI 저장소 장애", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TimePredictionController {
    private final TimePredictionService predictions;
    private final AiDraftService drafts;

    @PostMapping(value = "/ai/drafts", consumes = "application/json", produces = "application/json")
    @Operation(summary = "AI 작업 초안 발급", description = """
            ### 언제 호출하나요?
            AI 기능을 사용할 작업의 식별자가 필요할 때 호출합니다. 아직 투두를 저장하지 않습니다.

            | 상황 | 요청 본문 |
            |---|---|
            | 저장 전 새 작업 | `{}` |
            | 저장된 본인 투두의 시간 예측 | `{"todoId": 1}` |
            | 분해 후 사용자가 직접 추가한 세부 작업 | `{"parentDraftId": "원본 decompositionDraftId"}` |

            ### 응답 후 처리

            - 받은 `draftId`를 해당 작업의 편집·예측·저장 과정에서 계속 사용합니다.
            - 기존 투두는 같은 작업의 사용 이력에 연결됩니다. 새 초안을 받아도 성공 횟수가 초기화되지 않습니다.
            - 기존 투두의 세부 분량 나누기는 지원하지 않습니다(`NOT_APPLICABLE`).
            - 초안 발급에는 사용자별 호출 제한이 적용됩니다. 임시 결과가 만료되어도 성공 사용 이력은 유지됩니다.
            """)
    @ApiResponse(responseCode = "201", description = "AI 작업 초안 식별자 발급", content = @Content(schema = @Schema(implementation = AiDraftResponse.class)))
    @ApiResponse(responseCode = "429", description = "사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.CREATED)
    public AiDraftResponse draft(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AiDraftRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{}")))
            @Valid @RequestBody AiDraftRequest request
    ) {
        return drafts.issue(id(jwt), stamp(jwt), request);
    }

    @PostMapping(value = "/ai/time-predictions", consumes = "application/json", produces = "application/json")
    @Operation(summary = "사용자 예상시간에 대한 AI 보정 제안", description = """
            ### 호출 순서
            1. `/api/v1/ai/drafts`에서 `draftId`를 받습니다.
            2. 태스크명과 사용자가 직접 입력한 예상 작업 소요시간(분)을 보냅니다.
            3. 응답의 `aiEstimatedDurationMinutes`를 직접 입력시간과 함께 표시합니다.
            4. 사용자 선택 후 투두 생성·수정 API에 `estimateChoice`를 보냅니다. AI 선택 시 `predictionToken`이 필수입니다.

            ### 실패·재요청 처리

            - HTTP 200이어도 `mode=STATISTICAL_BASELINE`이면 Claude 대신 개인화 통계로 계산한 대체 제안입니다. `claudeStatus`도 함께 확인합니다.
            - 작업별 Claude 보정 성공은 1회입니다. 같은 입력의 재요청은 보존 결과를 돌려주며 다른 입력은 409입니다.
            - 편집·거절·토큰 만료로 성공 횟수가 초기화되지 않습니다. 실패·비활성·대체 결과는 성공 횟수를 사용하지 않습니다.
            - 제안 토큰은 10분 유효합니다. 같은 성공 결과를 다시 요청하면 외부 AI 호출 없이 토큰을 재발급합니다.
            - AI를 사용하지 않아도 일반 투두를 저장할 수 있습니다. 프로필 재계산·유사 검색은 서버가 처리하므로 선행 호출할 필요가 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "작업 시간 보정 제안", content = @Content(schema = @Schema(implementation = TimePredictionResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimePredictionResponse predict(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimePredictionRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"draftId\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"기획안 초안 작성\",\"sourceTaskName\":null,\"memo\":null,\"userEstimatedDurationMinutes\":30}")))
            @Valid @RequestBody TimePredictionRequest request
    ) {
        return predictions.predict(id(jwt), stamp(jwt), request);
    }

    @PostMapping(value = "/ai/time-predictions/batch", consumes = "application/json", produces = "application/json")
    @Operation(summary = "세부 작업별 AI 시간 보정 제안", description = """
            ### 언제 호출하나요?
            AI 세부 분량 나누기 결과를 편집하고 각 작업의 예상시간을 직접 입력한 뒤 호출합니다.

            ### 입력·응답

            - `decompositionDraftId`: 원본 분해 초안 ID입니다.
            - `tasks`: 같은 분해 결과에 속한 세부 작업들의 `draftId`·태스크명·직접 입력시간입니다. 중복 ID는 허용하지 않습니다.
            - 서버가 각 작업의 시간을 개별 보정하고 합계를 계산합니다. 전체 시간을 일정 비율로 나누지 않습니다.
            - 최종 저장 시 이 응답의 묶음 `predictionToken`을 사용합니다.

            ### 재요청
            각 작업에 성공 1회 제한을 적용합니다. 이미 성공한 작업은 같은 입력이면 재사용하며 실패 항목은 재시도할 수 있습니다. `predictions`의 작업별 상태를 확인합니다.
            """)
    @ApiResponse(responseCode = "200", description = "세부 작업별 시간 보정 제안", content = @Content(schema = @Schema(implementation = TimePredictionBatchResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimePredictionBatchResponse predictBatch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimePredictionBatchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"decompositionDraftId\":\"11111111-1111-4111-8111-111111111111\",\"sourceTaskName\":\"기획안 초안 작성\",\"memo\":null,\"tasks\":[{\"draftId\":\"22222222-2222-4222-8222-222222222222\",\"title\":\"핵심 메시지 한 줄 정하기\",\"userEstimatedDurationMinutes\":10}]}")))
            @Valid @RequestBody TimePredictionBatchRequest request
    ) {
        return predictions.batch(id(jwt), stamp(jwt), request);
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
