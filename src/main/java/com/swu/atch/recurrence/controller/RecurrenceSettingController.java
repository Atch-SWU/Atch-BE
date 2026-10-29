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
public class RecurrenceSettingController {
    private final RecurrenceSettingService service;

    @GetMapping("/settings/recurrence")
    @Operation(summary = "반복 추천 전체 설정 조회", description = """
            새 패턴 감지·추천 사용 여부를 조회합니다. 이 설정을 꺼도 등록된 활성 Rule의 Todo 자동 생성은 계속됩니다. 타이머 설정과도 독립입니다.
            """)
    public RecurrenceSettingsResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.get(user(jwt), stamp(jwt));
    }

    @PatchMapping("/settings/recurrence")
    @Operation(summary = "반복 추천 전체 설정 변경", description = """
            repeatRecommendationEnabled=true/false를 보냅니다. null은 허용하지 않습니다. 끄면 새 감지·추천을 멈추고 다시 켜면 기존 유효 기록을 재평가합니다. 이번만 할게요·다시 제안하지 않기·규칙 삭제 경계는 유지합니다.
            """)
    public RecurrenceSettingsResponse update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecurrenceSettingsUpdateRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "요청 예시", value = "{\"repeatRecommendationEnabled\":true}")))
            @Valid @RequestBody RecurrenceSettingsUpdateRequest request) {
        return service.update(user(jwt), stamp(jwt), request);
    }

    private Long user(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
    private Long positive(Long id) {
        if (id == null || id <= 0) throw RecurrencePolicy.invalid();
        return id;
    }
}
