package com.swu.atch.ai.controller;

import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.swu.atch.ai.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.ai.dto.response.TimeEstimationProfileResponse;

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
public class TimeEstimationProfileController {
    private final ProfileService profiles;

    @GetMapping(value = "/ai/profile", produces = "application/json")
    @Operation(summary = "[선택 조회] 저장된 시간 예측 프로필 조회 — 선택 호출", description = """
            ### 호출 대상
            개인화 정보를 화면에 표시할 때 선택적으로 호출합니다. 시간 예측을 위한 필수 선행 API가 아닙니다.

            ### 응답 읽기

            - `durationCorrectionFactor`: 사용자의 평소 예상시간을 보정하는 배수입니다.
            - `sampleCount`: 계산에 사용한 유효 완료 작업 수입니다.
            - 신규 사용자의 유효 이력이 없으면 404 대신 `NO_HISTORY`·배수 1.0·표본 0·신뢰도 LOW를 반환합니다.
            - 이력이 있어도 아직 계산되지 않았다면 `NOT_CALCULATED`일 수 있습니다.
            - 저장본의 `updatedAt`은 마지막 저장 시각입니다. 새 계산 기준이 없는 `calculatedAsOf=null`과 구분합니다.
            """)
    @ApiResponse(responseCode = "200", description = "저장된 개인화 Profile 조회", content = @Content(schema = @Schema(implementation = TimeEstimationProfileResponse.class)))
    public TimeEstimationProfileResponse profile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return profiles.get(id(jwt), stamp(jwt));
    }

    @PostMapping(value = "/ai/profile/recalculate", produces = "application/json")
    @Operation(summary = "[점검용] 시간 예측 프로필 재계산", description = """
            ### 호출 대상
            개인화 계산 결과를 직접 갱신·확인할 필요가 있을 때만 선택적으로 호출합니다. 인증된 본인 프로필을 처리하는 API이며 서버 전용으로 제한된 경로는 아닙니다.

            ### 일반 화면 연동
            시간 예측 과정에서 서버가 재계산하므로 프론트에서 먼저 호출할 필요가 없습니다. 서버 내부의 기본 보정시간(Baseline) 계산을 위한 별도 API도 없습니다.

            ### 계산에 사용하는 기록
            삭제되지 않은 완료 투두 중 사용자가 직접 입력한 양수 예상시간과 실제 종료 세션 시간이 있는 기록을 사용합니다. 반복 일정에서 복사한 예상시간은 제외합니다. 유효 기록이 없으면 불필요한 신규 프로필을 저장하지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "개인화 Profile 재계산", content = @Content(schema = @Schema(implementation = TimeEstimationProfileResponse.class)))
    public TimeEstimationProfileResponse recalculate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return profiles.recalculate(id(jwt), stamp(jwt));
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
