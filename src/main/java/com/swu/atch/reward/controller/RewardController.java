package com.swu.atch.reward.controller;

import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.reward.dto.request.RewardSettlementListQuery;
import com.swu.atch.reward.dto.response.RewardSettlementPageResponse;
import com.swu.atch.reward.dto.response.RewardSettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

// 확정된 보상 API 계약. Service 연결 전이며 계약 확인 모드의 유효 요청은 501을 반환한다.
// 설계 근거: docs/history/2026-11-05-reward/development-log.md
@RestController
@RequestMapping("/api/v1/rewards/settlements")
@ConditionalOnProperty(name = "app.reward.api-contracts.enabled", havingValue = "true")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "08. [설계·미구현] 캐릭터 · 보상", description = "확정된 API·DTO 계약이며 Service 연결 전입니다. 기본 비활성이며, 계약 확인 모드의 유효 요청은 501을 반환합니다.")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "구현 목표: 입력 누락·날짜 형식·기간 순서/범위 오류, 오늘·미래 날짜 조회", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태의 토큰", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "구현 목표: 확정 기록의 사용자·날짜·Snapshot 모순. 기존 정산을 덮어쓰지 않습니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "구현 목표: 일일 확정/보상 정산 누락 복구 중 또는 저장소 장애. 지급 없음으로 처리하지 말고 재조회합니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 계약 초안의 응답입니다. 아래 성공 응답은 구현할 목표 형식입니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RewardController {

    @GetMapping
    @Operation(summary = "[설계] 일별 보상 정산·지급 결과 목록", description = """
            종료된 서울 날짜의 확정 보상을 조회합니다. from≤to, 최대 366일, to는 어제 이하입니다.
            - 보상 날짜 내림차순이며 page=0부터, size 기본 20·최대 100입니다.
            - eligibleGrades는 지급 대상 등급, grants는 실제 지급 결과입니다. grants=[]도 정산 완료 결과입니다.
            - 처음 보는 grantId의 아이템을 안내하고, 확인한 ID는 로그인 사용자별로 클라이언트에 기억합니다.
            - 현재 장착·전체 수집 상태는 GET /api/v1/character로 조회합니다.
            - 미정산 복구 중인 503을 지급 없음으로 처리하지 않습니다. 별도 보상 청구·정산 실행·팝업 확인 API는 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "구현 목표: 지급이 없는 날도 포함한 정산 페이지")
    public RewardSettlementPageResponse list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject RewardSettlementListQuery query) {
        throw notImplemented();
    }

    @GetMapping("/{rewardDate}")
    @Operation(summary = "[설계] 날짜별 확정 보상 상세", description = """
            rewardDate에 종료된 서울 날짜(YYYY-MM-DD)를 보냅니다. 사용자 ID·완료율·등급은 보내지 않습니다.
            - 오늘·미래는 400, 기존 정산과 확정 근거가 모두 없는 날짜는 404, 미정산 복구 중이면 503입니다.
            - 확정 수·완료율·지급 ID는 이후 Todo 수정·완료 취소로 바뀌지 않습니다.
            - grants=[]는 정산 성공·실제 지급 없음입니다. 아이템 이름·자산은 현재 카탈로그 값입니다.
            """)
    @ApiResponse(responseCode = "200", description = "구현 목표: 확정된 일일 보상 정산 및 실제 지급 결과")
    @ApiResponse(responseCode = "404", description = "구현 목표: 해당 날짜의 기존 정산·확정 근거가 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public RewardSettlementResponse detail(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "조회할 종료된 서울 날짜. 목록 응답의 rewardDate를 사용할 수 있습니다.", example = "2026-10-29")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rewardDate) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "보상 API는 계약 설계 단계입니다.");
    }
}
