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
@Tag(name = "AI 세부 작업", description = "FR-AI-007~013. 선택 작업 내용·시간 직접 입력·최종 승인 후 저장")
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
    @Operation(summary = "세부 작업 이름·순서 제안", description = "작업명·선택적인 짧은 한 줄 작업 내용(taskDescription)·기간·메모를 활용합니다. 분량 전용 필드가 아닙니다. 시간 초깃값 없이 이름·순서·세부 draftId만 반환합니다. 원본 초안의 성공 분해는1회이며 동일 요청은 보존 결과, 다른 입력은409입니다. 실패는 성공을 소비하지 않습니다. 결과는 편집·추가·삭제 가능하며 수동 추가 항목은 /ai/drafts에서 식별자를 받습니다. 아직 Todo를 저장하지 않고 실패·미사용 시 일반 Todo를 만들 수 있습니다.")
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
    @Operation(summary = "최종 승인한 세부 Todo 일괄 생성", description = "원본 부모 Todo 없이 확정한1~20개만 하나의 트랜잭션으로 저장하고 sourceTaskName·공통 일정·메모를 복사합니다. 배열 순서는 응답에 유지하지만 별도 영구 정렬 컬럼은 추가하지 않습니다. 직접 입력 합계는 각 작업의 사용자 분 합계입니다. USER 선택은 입력 분, AI 선택은 해당 작업별 서버 제안 분을 적용하고 사용자 분을 덮어쓰지 않습니다. estimateChoice 생략은 USER입니다. 유효한 USER 선택 토큰은 거절한 AI 값도 보존합니다. AI 토큰의 소유·입력·10분 유효기간을 확인합니다. 서버가 직접 초를 입력받거나 AI 재호출하지 않습니다. 같은 creationRequestId·같은 본문은 기존 결과, 다른 본문은409입니다. 원자 저장·영속 재전송 검증을 적용합니다.")
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
