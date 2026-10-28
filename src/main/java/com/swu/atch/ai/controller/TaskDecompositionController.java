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
@Tag(name = "11. [프론트] AI 세부 분량 나누기", description = "세부 작업 제안 → 편집·시간 입력 → 선택적 시간 예측 → 최종 승인 저장 순서입니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태는 Service에서 검증", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증·AI 저장소 장애", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TaskDecompositionController {
    private final TaskDecompositionService decompositions;
    private final AiTodoService aiTodos;

    @PostMapping(value = "/ai/task-decompositions", consumes = "application/json", produces = "application/json")
    @Operation(summary = "AI 세부 분량 나누기 — 작업 제안", description = """
            ### 호출 순서
            1. 새 작업의 `draftId`를 발급받습니다.
            2. 태스크명·일정과 선택 입력인 작업 내용·메모를 보냅니다.
            3. 응답의 세부 작업 이름·순서를 화면에 표시합니다. 이 단계에서는 투두가 저장되지 않습니다.

            ### 편집 후 다음 단계

            - 사용자가 이름·순서를 수정하고 항목을 추가·삭제할 수 있습니다.
            - 수동 추가 항목은 `/api/v1/ai/drafts`에 `parentDraftId`를 보내 새 ID를 받습니다.
            - 시간 초깃값은 제안하지 않습니다. 각 세부 작업의 예상시간은 사용자가 직접 입력합니다.
            - 필요하면 묶음 시간 예측 후 `/api/v1/todos/decomposed`로 최종 저장합니다.

            ### 제한·실패
            원본 초안별 성공은 1회입니다. 동일 요청은 보존 결과를 반환하고 다른 입력은 409입니다. 실패는 재시도 가능하며 분해를 사용하지 않고 일반 투두를 만들 수도 있습니다. `taskDescription`은 분량 전용 값이 아닌 선택적인 한 줄 작업 내용입니다.
            """)
    @ApiResponse(responseCode = "200", description = "세부 작업 이름·순서 제안", content = @Content(schema = @Schema(implementation = TaskDecompositionResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "외부 분해 실패·잘못된 구조화 응답", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "외부 분해 시간 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TaskDecompositionResponse decompose(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskDecompositionRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"draftId\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"기획안 초안 작성\",\"taskDescription\":\"발표에서 문제와 해결 방향을 설명할 기획안\",\"startDate\":\"2026-10-29\",\"startTime\":\"18:00:00\",\"dueDate\":\"2026-10-29\",\"dueTime\":\"20:00:00\",\"allDay\":false,\"memo\":null}")))
            @Valid @RequestBody TaskDecompositionRequest request
    ) {
        return decompositions.decompose(id(jwt), stamp(jwt), request);
    }

    @PostMapping(value = "/todos/decomposed", consumes = "application/json", produces = "application/json")
    @Operation(summary = "최종 승인한 세부 투두 일괄 저장", description = """
            ### 언제 호출하나요?
            세부 작업 편집·개별 예상시간 입력·AI 제안 수락 여부를 모두 확정한 뒤 한 번 호출합니다.

            ### 저장할 내용

            - `tasks`에 확정한 1~20개만 보냅니다. 원본 부모 투두는 생성하지 않습니다.
            - 원본 태스크명·공통 일정·메모를 각 투두에 복사합니다.
            - `estimateChoice` 생략 또는 USER 선택은 직접 입력시간, AI 선택은 서버 제안시간을 사용합니다. 직접 입력한 원본 시간은 따로 보존합니다.
            - 묶음의 선택은 전체에 적용됩니다. `estimateChoice.draftId`는 원본 `decompositionDraftId`를 사용합니다.
            - 응답 배열은 요청 순서를 따르며 별도 영구 정렬 순서를 저장하지는 않습니다.

            ### 중복 저장 방지
            프론트가 생성한 `creationRequestId`를 보냅니다. 같은 ID·같은 본문 재전송은 기존 결과를 반환하고 다른 본문은 409입니다. 전체가 함께 저장되거나 함께 실패합니다. 이 저장 API는 AI를 다시 호출하지 않습니다.
            """)
    @ApiResponse(responseCode = "201", description = "최종 승인한 세부 Todo 일괄 생성", content = @Content(schema = @Schema(implementation = DecomposedTodoCreateResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "200", description = "동일 생성 요청의 기존 결과", content = @Content(schema = @Schema(implementation = DecomposedTodoCreateResponse.class)))
    public org.springframework.http.ResponseEntity<DecomposedTodoCreateResponse> confirm(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DecomposedTodoCreateRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"creationRequestId\":\"33333333-3333-4333-8333-333333333333\",\"decompositionDraftId\":\"11111111-1111-4111-8111-111111111111\",\"sourceTaskName\":\"기획안 초안 작성\",\"memo\":null,\"startDate\":\"2026-10-29\",\"startTime\":\"18:00:00\",\"dueDate\":\"2026-10-29\",\"dueTime\":\"20:00:00\",\"allDay\":false,\"tasks\":[{\"draftId\":\"22222222-2222-4222-8222-222222222222\",\"title\":\"핵심 메시지 한 줄 정하기\",\"userEstimatedDurationMinutes\":10}],\"estimateChoice\":{\"draftId\":\"11111111-1111-4111-8111-111111111111\",\"selectedEstimateType\":\"USER\",\"predictionToken\":null}}")))
            @Valid @RequestBody DecomposedTodoCreateRequest request
    ) {
        var result = aiTodos.confirm(id(jwt), stamp(jwt), request);
        return org.springframework.http.ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
