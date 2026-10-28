package com.swu.atch.session.controller;

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
import com.swu.atch.session.dto.request.*;
import com.swu.atch.session.dto.response.*;
import org.springdoc.core.annotations.ParameterObject;
import java.util.UUID;

// 명세 전용 opt-in. 기본 실행에는 등록하지 않으며 도메인 구현 완료 시 조건과 501을 제거한다.
// JWT의 본인 ID·credential_stamp를 후속 Service에 전달한다. 현재 DB/Redis/외부 AI를 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.api.contracts.enabled", havingValue = "true")
@Tag(name = "SESSION·타이머", description = "FR-SESS-001~013. 시작·일시정지·재개·전환·완료 결과 [명세 단계: 현재 501]")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태 검증은 후속 Service", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 명세 단계. Service 미연결 - NOT_IMPLEMENTED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애. 후속 구현에서는 저장소 장애도 구분", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SessionController {
    private static final String CONTRACT_STAGE = "[명세 단계·현재 501] 아래 성공 동작과 비즈니스 오류는 후속 구현 계약입니다.\n\n";

    @PostMapping(value = "/todos/{todoId}/timer/start", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 시작", description = CONTRACT_STAGE + "FR-SESS-001~003. 서버 시각으로 Session을 생성하고 Todo를 IN_PROGRESS로 변경합니다. 사용자 전체 열린 구간 최대 1개, User→Todo 잠금 순서입니다. 기준 마지막 구간이 바뀐 지연 요청은 409입니다. 미완료 집중 대상이 다른 Todo이면 직접 시작으로 전환 제한을 우회하지 못합니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 타이머 시작", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse start(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerStartRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"expectedLastSessionId\":null}")))
            @Valid @RequestBody TimerStartRequest request
    ) {
        requirePositiveId(todoId);
        throw notImplemented();
    }

    @PostMapping(value = "/todos/{todoId}/timer/pause", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 일시정지", description = CONTRACT_STAGE + "FR-SESS-004·006. 지정한 열린 Session을 PAUSED로 종료하고 Todo도 PAUSED로 변경합니다. 일시정지해도 미완료 집중 대상은 유지합니다. 저장 초는 서버 시각으로 계산하며 0초를 허용합니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 타이머 일시정지", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse pause(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerPauseRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"sessionId\":1}")))
            @Valid @RequestBody TimerPauseRequest request
    ) {
        requirePositiveId(todoId);
        throw notImplemented();
    }

    @PostMapping(value = "/todos/{todoId}/timer/resume", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 재개", description = CONTRACT_STAGE + "FR-SESS-005·007. 마지막 종료 구간을 확인하고 새 Session을 만듭니다. 기존 시작 시각을 덮어쓰지 않습니다. 전환 대기 중 대상 불일치·상태 변경은 409입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 타이머 재개", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse resume(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerResumeRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"lastSessionId\":1}")))
            @Valid @RequestBody TimerResumeRequest request
    ) {
        requirePositiveId(todoId);
        throw notImplemented();
    }

    @GetMapping(value = "/timer/current", produces = "application/json")
    @Operation(summary = "현재 집중 대상·실행·전환 대기 조회", description = CONTRACT_STAGE + "FR-SESS-003. 대상이 없으면 IDLE·null 식별자로 200입니다. 일시정지한 미완료 대상도 반환합니다. 저장된 닫힌 초와 실행 중 경과 초를 구분하고 서버 시각을 제공합니다. 자정을 지나도 구간은 유지하며 통계만 서울 날짜별 분리합니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 현재 집중 대상·실행·전환 대기 조회", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    public TimerStateResponse current(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        throw notImplemented();
    }

    @PostMapping(value = "/timer/switches", consumes = "application/json", produces = "application/json")
    @Operation(summary = "작업 전환 요청", description = CONTRACT_STAGE + "FR-SESS-012·013. 일시정지 여부와 관계없이 가장 최근 시작한 미완료 집중 대상에서 다른 작업으로 갈 때 제한합니다. 실행 중이면 SWITCHED로 종료하고 대기 동안 집중 초를 누적하지 않습니다. 대기 시작 당시 설정으로 readyAt을 고정합니다. 제한 비활성은 즉시 STARTED입니다. Redis 대기 상태 유실·만료만으로 기존 미완료 집중 대상이 해제되지는 않습니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 작업 전환 요청", content = @Content(schema = @Schema(implementation = TimerSwitchResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerSwitchResponse requestSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerSwitchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"sourceTodoId\":1,\"targetTodoId\":2,\"sourceSessionId\":1}")))
            @Valid @RequestBody TimerSwitchRequest request
    ) {
        throw notImplemented();
    }

    @PostMapping(value = "/timer/switches/{switchId}/confirm", produces = "application/json")
    @Operation(summary = "대기 완료 후 전환 확정", description = CONTRACT_STAGE + "서버 readyAt 이후 본인 요청·설정 당시 조건·대상 상태를 다시 확인하고 새 구간을 시작합니다. 대기 중 DB 잠금을 유지하지 않습니다. 아직 대기 중이거나 원래 상태가 바뀌면 409입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 대기 완료 후 전환 확정", content = @Content(schema = @Schema(implementation = TimerSwitchResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerSwitchResponse confirmSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 전환 요청 ID") @PathVariable UUID switchId
    ) {
        throw notImplemented();
    }

    @DeleteMapping("/timer/switches/{switchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "전환 요청 취소", description = CONTRACT_STAGE + "대기를 취소하되 기존 작업을 자동 재개하지 않습니다. 본인 요청 확인·만료 처리는 후속 구현입니다.")
    @ApiResponse(responseCode = "204", description = "[구현 후] 취소 완료", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void cancelSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 전환 요청 ID") @PathVariable UUID switchId
    ) {
        throw notImplemented();
    }

    @GetMapping(value = "/todos/{todoId}/sessions", produces = "application/json")
    @Operation(summary = "Todo 집중 구간 목록", description = CONTRACT_STAGE + "본인 활성 Todo의 구간을 startedAt,id 오름차순으로 조회합니다. page=0,size=20,최대100입니다. 열린 구간은 종료 필드가 null입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] Todo 집중 구간 목록", content = @Content(schema = @Schema(implementation = SessionPageResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SessionPageResponse sessions(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1") @PathVariable Long todoId,
            @Valid @ModelAttribute @ParameterObject SessionPageQuery query
    ) {
        requirePositiveId(todoId);
        throw notImplemented();
    }

    @GetMapping(value = "/todos/{todoId}/completion-summary", produces = "application/json")
    @Operation(summary = "완료 결과·다음 작업 후보", description = CONTRACT_STAGE + "FR-SESS-008~011. 완료 명령은 기존 POST /todos/{todoId}/complete를 재사용하고 응답 형식도 유지합니다. 이 조회는 완료된 Todo의 전체 누적 실제 초, PAUSED 횟수, PAUSED/SWITCHED 이후 재개 횟수, SWITCHED 횟수, max(완료시각-마감,0)와 다음 후보 최대3개를 반환합니다. 최초 시작과 완료 취소 후 시작을 재개로 세지 않습니다. 완료 취소는 과거 구간을 지우지 않습니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 완료 결과·다음 작업 후보", content = @Content(schema = @Schema(implementation = TodoCompletionSummaryResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] 상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "[구현 후] 본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoCompletionSummaryResponse completionSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1") @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        throw notImplemented();
    }

    private void requirePositiveId(Long todoId) {
        if (todoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo ID는 양수여야 합니다.");
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SESSION·타이머 Service 연결 전입니다.");
    }
}

