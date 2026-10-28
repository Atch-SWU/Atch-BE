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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.ai.dto.request.SimilarTodoSearchRequest;
import com.swu.atch.ai.dto.response.SimilarTodoSearchResponse;

// 명세 전용 opt-in. 기본 실행에는 등록하지 않으며 도메인 구현 완료 시 조건과 501을 제거한다.
// JWT의 본인 ID·credential_stamp를 후속 Service에 전달한다. 현재 DB/Redis/외부 AI를 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.api.contracts.enabled", havingValue = "true")
@Tag(name = "유사 작업 검색", description = "Voyage·VECTOR(1024) 저장 구조는 기존 데이터 계층 재사용 [명세 단계: 현재 501]")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태 검증은 후속 Service", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 명세 단계. Service 미연결 - NOT_IMPLEMENTED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애. 후속 구현에서는 저장소 장애도 구분", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SimilarTodoController {
    private static final String CONTRACT_STAGE = "[명세 단계·현재 501] 아래 성공 동작과 비즈니스 오류는 후속 구현 계약입니다.\n\n";

    @PostMapping(value = "/ai/similar-todos", consumes = "application/json", produces = "application/json")
    @Operation(summary = "본인 유사 완료 작업 검색", description = CONTRACT_STAGE + "본인 유효 완료·미삭제·기준 시각 이전 기록 중 현재 모델·1024차원·원문 해시가 일치하는 최대3개를 Exact Search로 찾습니다. stale 벡터는 LIMIT 전에 제외합니다. 자기 자신과 타인 데이터는 제외합니다. 외부 색인 호출은 완료 트랜잭션 밖에서 처리하고 색인 일부 준비·미활성·실패를 기록 없음과 구분합니다. 이 독립 검색에도 별도 사용자별 호출 제한을 둡니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 본인 유사 완료 작업 검색", content = @Content(schema = @Schema(implementation = SimilarTodoSearchResponse.class)))
    @ApiResponse(responseCode = "429", description = "[구현 후] 사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SimilarTodoSearchResponse search(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SimilarTodoSearchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"title\":\"기획안 초안 작성\",\"sourceTaskName\":null,\"memo\":null,\"excludeTodoId\":null}")))
            @Valid @RequestBody SimilarTodoSearchRequest request
    ) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "유사 작업 검색 Service 연결 전입니다.");
    }
}

