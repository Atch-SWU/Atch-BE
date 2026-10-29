package com.swu.atch.recurrence.controller;

import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.recurrence.dto.request.*;
import com.swu.atch.recurrence.dto.response.*;
import com.swu.atch.recurrence.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력 형식·필수값·조건부 시각/마감/요일 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "로그인 없음·삭제 계정·변경 전 인증 토큰", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "없는 ID·타인 소유·삭제된 규칙", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "추천 꺼짐·오래된 화면·상태 변경·규칙 중첩. code를 확인하고 최신 정보를 조회합니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "일일 확정 또는 당일 회차 준비 실패. 잠시 후 재시도합니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@Tag(name = "07. [프론트] 반복 일정")
public class RecurrenceRecommendationController {
    private final RecurrenceRecommendationService service;

    @GetMapping("/recurrence-recommendations")
    @Operation(summary = "반복 등록 추천 목록", description = """
            ### 언제 호출하나요?

            1. 일반 투두 생성 API가 성공하면 먼저 저장 완료를 표시하고 투두 목록을 갱신합니다.
            2. 그다음 이 API를 별도로 호출합니다. 추천 응답을 기다리는 동안에도 사용자가 다음 작업을 할 수 있게 합니다.
            3. `enabled=true`이고 `items`가 있으면 카드·팝업 등으로 반복 등록을 안내합니다. 별도 추천 화면은 필수가 아닙니다.

            ### 서버는 무엇을 하나요?

            - 호출 시 본인의 기록을 원래 예정 시작 일시 기준으로 다시 확인하고 추천을 갱신합니다. 백그라운드 상시 감지·푸시 알림 방식이 아닙니다.
            - 같은 태스크명·하루종일 여부·시각을 기준으로 연속 2일이면 매일, 같은 요일에 연속 2주면 매주 패턴을 감지합니다. 수행 완료 여부·예상/실제 시간 비교와는 별개의 감지입니다.
            - 미래 예정 일정·삭제된 투두·규칙으로 자동 생성된 투두는 감지에서 제외합니다. 같은 날짜의 중복 일정은 한 번만 세며, 이 GET을 여러 번 호출해도 발생 횟수가 늘어나지 않습니다.

            ### 응답 후 처리

            - `items`는 본인의 현재 추천 목록이며 방금 저장한 투두에 대한 추천만 담는 것은 아닙니다. 같은 추천이 다시 보일 수 있으므로 `patternId`로 식별합니다.
            - 태스크명·DAILY(매일)/WEEKLY(매주)·요일·하루종일 여부·시각을 표시합니다.
            - 사용자가 등록 / 이번만 할게요 / 다시 제안하지 않기 중 하나를 선택합니다.
            - 등록 폼 기본값은 templateSuggestion이며 사용자가 확인해 보냅니다.
            - enabled=false이면 추천 기능이 꺼진 상태이며 빈 목록을 반환합니다. 등록된 규칙의 생성은 별개입니다.
            - 추천 조회가 실패해도 앞서 성공한 투두 저장은 유지합니다. 추천 오류만 별도로 처리하고 이 오류 때문에 투두 생성 요청을 다시 보내지 않습니다.
            """)
    public RecurrenceRecommendationPageResponse list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject RecurrenceRecommendationQuery query) {
        return service.list(user(jwt), stamp(jwt), query);
    }

    @PostMapping("/recurrence-recommendations/{patternId}/skip")
    @Operation(summary = "이번만 할게요", description = """
            추천 목록의 updatedAt을 expectedUpdatedAt으로 보냅니다. 탐지 횟수를 0으로 초기화하고 이후 새로 만든 기록부터 다시 감지합니다. 기존 투두는 유지하며 이 API가 새 투두를 만들지는 않습니다. 오래된 추천이면 409 후 목록을 다시 조회합니다.
            """)
    public RecommendationActionResponse skip(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "추천 목록에서 받은 patternId입니다.") @PathVariable Long patternId, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationActionRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"expectedUpdatedAt\":\"2026-10-29T02:00:00Z\"}")))
            @Valid @RequestBody RecommendationActionRequest request) {
        return service.act(user(jwt), stamp(jwt), positive(patternId), request, false);
    }

    @PostMapping("/recurrence-recommendations/{patternId}/dismiss")
    @Operation(summary = "다시 제안하지 않기", description = """
            추천 목록의 updatedAt을 expectedUpdatedAt으로 보냅니다. 같은 이름·주기·요일·하루종일/시각의 패턴을 다시 제안하지 않도록 보존합니다. 해제 API는 제공하지 않습니다. DAILY와 WEEKLY는 별도 패턴입니다.
            """)
    public RecommendationActionResponse dismiss(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "추천 목록에서 받은 patternId입니다.") @PathVariable Long patternId, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecommendationActionRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"expectedUpdatedAt\":\"2026-10-29T02:00:00Z\"}")))
            @Valid @RequestBody RecommendationActionRequest request) {
        return service.act(user(jwt), stamp(jwt), positive(patternId), request, true);
    }


    private Long user(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
    private Long positive(Long id) {
        if (id == null || id <= 0) throw RecurrencePolicy.invalid();
        return id;
    }
}
