package com.swu.atch.reward.controller;

import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.reward.dto.response.RewardProgressResponse;
import com.swu.atch.reward.service.RewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rewards/progress")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RewardProgressController {
    private static final String B_TARGET_EXAMPLE = """
            {"date":"2026-11-28","asOf":"2026-11-28T06:00:00Z","assignedTodoCount":25,"completedTodoCount":12,
             "completionPercent":48,"nextReward":{"grade":"B","targetCompletionPercent":50,
             "remainingPercentagePoints":2,"remainingTodoCount":1}}
            """;
    private static final String NO_TODOS_EXAMPLE = """
            {"date":"2026-11-28","asOf":"2026-11-28T06:00:00Z","assignedTodoCount":0,"completedTodoCount":0,
             "completionPercent":0,"nextReward":null}
            """;

    private final RewardService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "오늘의 다음 아이템 보상까지 남은 완료율", tags = "08. [프론트] 캐릭터 · 보상", description = """
            홈의 보상 목표 카드에 표시할 본인의 서울 기준 오늘 진행도입니다. JWT와 서버 시각으로 조회 대상을 결정합니다.
            - 오늘로 이월된 투두와 당일 반복 투두를 포함해 배정·완료 수를 계산합니다. 논리 삭제된 투두는 집계에서 빠집니다.
            - B 50%, A 75%, S 100% 순서에서 아직 달성하지 않았고 활성 미보유 아이템이 있는 첫 등급을 반환합니다.
            - `nextReward.grade`와 `nextReward.remainingPercentagePoints`를 카드에 표시합니다. 현재 완료율이 48%이고 B 기준이 50%이면 차이는 2%p입니다.
            - 오늘 배정이 없거나 달성 가능한 다음 목표가 없으면 `nextReward`는 null입니다.
            - 홈 진입 또는 투두 생성·완료·완료 취소·삭제 후 조회하면 현재 진행도를 받을 수 있습니다. 실제 지급은 날짜가 끝난 뒤 일일 정산 결과에서 확인합니다.
            """)
    @ApiResponse(responseCode = "200", description = "오늘의 완료율과 다음 보상 목표",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RewardProgressResponse.class),
                    examples = {@ExampleObject(name = "B등급까지 2%p", value = B_TARGET_EXAMPLE),
                            @ExampleObject(name = "오늘 배정 없음", value = NO_TODOS_EXAMPLE)}))
    @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태의 토큰",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "일일 기록의 정합성 충돌",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "일일 확정·보상 정산·반복 일정 생성 준비 중 또는 저장소 장애",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public RewardProgressResponse progress(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.progress(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("credential_stamp"));
    }
}
