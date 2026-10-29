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
import com.swu.atch.ai.dto.request.SimilarTodoSearchRequest;
import com.swu.atch.ai.dto.response.SimilarTodoSearchResponse;

// 본인 사용자 ID와 현재 비밀번호 상태를 전달하고, 실제 처리·트랜잭션은 Service에 맡긴다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "06. AI · 개인화")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태는 Service에서 검증", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증·AI 저장소 장애", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SimilarTodoController {
    private final SimilarTodoService similar;

    @PostMapping(value = "/ai/similar-todos", consumes = "application/json", produces = "application/json")
    @Operation(summary = "[선택 조회] 본인의 유사한 완료 투두 검색 — 선택 호출", description = """
            ### 호출 대상
            유사한 과거 작업을 별도 화면에 보여줄 때 프론트가 선택적으로 호출할 수 있습니다. 서버 전용 API가 아닙니다.

            ### 입력·응답

            - 현재 태스크명과 선택적인 원본 태스크명·메모를 보냅니다.
            - 본인의 유효한 완료 작업 중 유사한 항목을 최대 3개 반환합니다. 타인 데이터는 포함하지 않습니다.
            - `voyageStatus`: AVAILABLE=검색 가능, PARTIAL=일부 기록만 검색 준비됨, NO_HISTORY=유효 이력 없음, DISABLED=기능 비활성, UNAVAILABLE=검색 이용 불가입니다.
            - 결과가 빈 배열이어도 이력 없음과 검색 장애를 같은 의미로 표시하지 않습니다.

            ### 시간 예측과의 관계
            시간 예측 API가 내부에서 유사 검색을 수행하므로 미리 호출할 필요가 없습니다. 독립 검색에는 사용자별 별도 호출 제한이 있습니다.
            """)
    @ApiResponse(responseCode = "200", description = "본인 유사 완료 작업 검색", content = @Content(schema = @Schema(implementation = SimilarTodoSearchResponse.class)))
    @ApiResponse(responseCode = "429", description = "사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SimilarTodoSearchResponse search(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SimilarTodoSearchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"title\":\"기획안 초안 작성\",\"sourceTaskName\":null,\"memo\":null,\"excludeTodoId\":null}")))
            @Valid @RequestBody SimilarTodoSearchRequest request
    ) {
        return similar.search(id(jwt), stamp(jwt), request);
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
