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
public class RecurrenceRuleController {
    private final RecurrenceRuleService service;

    @PostMapping("/recurrence-recommendations/{patternId}/register")
    @Operation(summary = "추천을 반복 일정으로 등록", description = """
            ### 입력
            추천의 patternId를 경로에, updatedAt을 expectedUpdatedAt에 복사합니다. 메모·기본 예상시간·마감 간격을 사용자가 확인해 보냅니다. 태스크명·주기·하루종일 여부·요일·시각은 서버가 추천에서 복사합니다.

            ### 생성 시점
            등록 시 활성 Rule을 만들고 첫 자동 생성은 다음 서울 날짜 이후 해당 요일부터 시작합니다. 미래 Todo를 미리 만들지 않습니다. 하루종일은 시각 없이 마감 간격 1440분(생략 가능)으로 고정합니다.

            201은 신규 등록, 200은 같은 등록 요청의 보존 결과입니다. 응답 유실 시 같은 본문을 재전송할 수 있습니다. 다른 값·삭제된 출처·상태 변경은 409입니다.
            """)
    @ApiResponse(responseCode = "201", description = "반복 규칙 등록 및 Location 반환")
    public ResponseEntity<RecurrenceRuleResponse> register(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "추천 목록에서 받은 patternId입니다.") @PathVariable Long patternId, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecurrenceRuleRegisterRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"memo\":\"교재 복습\",\"defaultDeadlineOffsetMinutes\":60,\"defaultUserEstimatedDurationMinutes\":30,\"expectedUpdatedAt\":\"2026-10-29T02:00:00Z\"}")))
            @Valid @RequestBody RecurrenceRuleRegisterRequest request) {
        var result = service.register(user(jwt), stamp(jwt), positive(patternId), request);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header("Location", "/api/v1/recurrence-rules/" + result.rule().id()).body(result.rule());
    }

    @GetMapping("/recurrence-rules")
    @Operation(summary = "등록된 반복 일정 목록", description = """
            설정의 반복 일정 관리 화면에서 조회합니다. enabled 생략은 활성·비활성 전체이며 삭제된 규칙은 제외합니다. page는 0부터 시작합니다. 규칙 enabled는 전체 추천 설정과 독립입니다.
            """)
    public RecurrenceRulePageResponse list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject RecurrenceRuleListQuery query) {
        return service.list(user(jwt), stamp(jwt), query);
    }

    @GetMapping("/recurrence-rules/{ruleId}")
    @Operation(summary = "반복 일정 상세·편집 폼", description = """
            기본 정보와 updatedAt을 조회합니다. 수정·활성 변경에는 이 updatedAt을 그대로 복사합니다. generationNotBefore는 서버의 최초 생성 가능 예정 일시이며 프론트 입력값이 아닙니다.
            """)
    public RecurrenceRuleResponse detail(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "규칙 목록·상세의 id입니다.") @PathVariable Long ruleId) {
        return service.detail(user(jwt), stamp(jwt), positive(ruleId));
    }

    @PutMapping("/recurrence-rules/{ruleId}")
    @Operation(summary = "반복 일정 전체 수정", description = """
            ### 입력
            수정 폼 전체와 expectedUpdatedAt을 보냅니다. memo=null은 기존 메모 삭제입니다. DAILY/WEEKLY 주기 자체는 바꿀 수 없습니다.

            ### 하루종일·시각 지정
            allDay=true는 scheduledTime=null, 마감 간격은 생략 또는 1440입니다. false는 예정 시각과 양수 마감 간격이 필요합니다. WEEKLY는 요일 필수, DAILY는 요일 null입니다.

            기존 Todo는 유지하고 앞으로 생성할 회차만 변경합니다. 이미 생성·삭제된 회차를 다시 만들지 않습니다. 오래된 화면은 409 후 다시 조회합니다.
            """)
    public RecurrenceRuleResponse update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "규칙 목록·상세의 id입니다.") @PathVariable Long ruleId, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecurrenceRuleUpdateRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"taskName\":\"교재 복습\",\"memo\":null,\"dayOfWeek\":null,\"allDay\":false,\"scheduledTime\":\"09:00:00\",\"defaultDeadlineOffsetMinutes\":60,\"defaultUserEstimatedDurationMinutes\":30,\"expectedUpdatedAt\":\"2026-10-29T02:00:00Z\"}")))
            @Valid @RequestBody RecurrenceRuleUpdateRequest request) {
        return service.update(user(jwt), stamp(jwt), positive(ruleId), request);
    }

    @PatchMapping("/recurrence-rules/{ruleId}/enabled")
    @Operation(summary = "개별 반복 일정 활성·비활성 변경", description = """
            enabled와 직전 응답의 updatedAt을 보냅니다. false이면 새 Todo 생성을 멈추며 기존 Todo는 유지합니다. 다시 켜면 활성화 시점 이후 예정 회차부터 생성하고 지난 회차를 소급 생성하지 않습니다. 하루종일의 시작은 00:00이므로 낮에 재활성화하면 다음 대상일부터 생성합니다.
            """)
    public RecurrenceRuleResponse enabled(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "규칙 목록·상세의 id입니다.") @PathVariable Long ruleId, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecurrenceRuleEnabledRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"enabled\":false,\"expectedUpdatedAt\":\"2026-10-29T02:00:00Z\"}")))
            @Valid @RequestBody RecurrenceRuleEnabledRequest request) {
        return service.enable(user(jwt), stamp(jwt), positive(ruleId), request);
    }

    @DeleteMapping("/recurrence-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "반복 일정 삭제", description = """
            논리 삭제로 새 회차 생성만 중단합니다. 기존 Todo·세션·일별 기록·원본 Pattern은 유지합니다. 이후 새로 직접 작성한 기록에서 새 Pattern을 감지할 수 있으며 삭제 전 기록으로 즉시 재추천하지 않습니다. 본인의 이미 삭제된 규칙은 재요청도 204입니다.
            """)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Parameter(description = "규칙 목록·상세의 id입니다.") @PathVariable Long ruleId) {
        service.delete(user(jwt), stamp(jwt), positive(ruleId));
    }

    private Long user(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
    private Long positive(Long id) {
        if (id == null || id <= 0) throw RecurrencePolicy.invalid();
        return id;
    }
}
