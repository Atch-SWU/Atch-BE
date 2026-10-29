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
import com.swu.atch.session.service.TimerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.session.dto.request.*;
import com.swu.atch.session.dto.response.*;
import org.springdoc.core.annotations.ParameterObject;
import java.util.UUID;

// 인증된 사용자와 서버 시각을 기준으로 처리한다. 외부 AI는 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "04. [프론트] 작업 세션 · 타이머")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·범위·관계 검증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 일일 확정 복구 대기", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class SessionController {
    private final TimerService service;

    @PostMapping(value = "/todos/{todoId}/timer/start", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 시작", description = """
            ### 언제 호출하나요?
            선택한 투두의 작업을 처음 시작할 때 호출합니다. `expectedLastSessionId`에는 **그 투두**의 마지막 세션 ID를 보냅니다. 한 번도 실행하지 않았다면 null입니다.

            ### 처리 결과·충돌

            - 서버가 작업 세션을 만들고 투두를 진행 중(IN_PROGRESS)으로 바꿉니다. 한 사용자에게 동시에 실행 중인 세션은 최대 1개입니다.
            - 오래된 화면의 마지막 세션 정보가 현재와 다르면 409입니다. 현재 타이머·해당 투두 세션 목록을 다시 조회합니다.
            - 다른 미완료 작업이 집중 대상으로 남아 있으면 작업 전환 API를 사용해야 합니다. 일시정지한 대상도 포함됩니다.
            """)
    @ApiResponse(responseCode = "200", description = "타이머 시작", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse start(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 투두 ID. 생성·목록·상세 응답의 id를 사용합니다.", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerStartRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"expectedLastSessionId\":null}")))
            @Valid @RequestBody TimerStartRequest request
    ) {
        requirePositiveId(todoId);
        return service.start(id(jwt), stamp(jwt), todoId, request);
    }

    @PostMapping(value = "/todos/{todoId}/timer/pause", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 일시정지", description = """
            ### 입력
            현재 타이머의 `sessionId`를 보냅니다. 경로의 `todoId`도 현재 집중 대상과 같아야 합니다.

            ### 응답 후 처리
            실행 구간을 종료하고 투두를 일시정지(PAUSED)로 바꿉니다. 실제 집중시간은 서버 시각으로 저장하며 0초도 가능합니다. 집중 대상은 유지됩니다. 다시 시작할 때는 재개 API를 사용합니다.
            """)
    @ApiResponse(responseCode = "200", description = "타이머 일시정지", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse pause(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 투두 ID. 생성·목록·상세 응답의 id를 사용합니다.", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerPauseRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"sessionId\":1}")))
            @Valid @RequestBody TimerPauseRequest request
    ) {
        requirePositiveId(todoId);
        return service.pause(id(jwt), stamp(jwt), todoId, request);
    }

    @PostMapping(value = "/todos/{todoId}/timer/resume", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 재개", description = """
            ### 입력
            재개할 투두의 마지막 종료 세션 ID를 `lastSessionId`로 보냅니다.

            ### 응답 후 처리
            새 작업 세션이 시작됩니다. 이전 구간의 시작 시각·집중시간은 유지합니다. 전환 대기나 투두·세션 상태가 화면과 달라졌으면 409이므로 현재 상태를 다시 조회합니다.
            """)
    @ApiResponse(responseCode = "200", description = "타이머 재개", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerStateResponse resume(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 투두 ID. 생성·목록·상세 응답의 id를 사용합니다.", example = "1") @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerResumeRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"lastSessionId\":1}")))
            @Valid @RequestBody TimerResumeRequest request
    ) {
        requirePositiveId(todoId);
        return service.resume(id(jwt), stamp(jwt), todoId, request);
    }

    @GetMapping(value = "/timer/current", produces = "application/json")
    @Operation(summary = "현재 타이머·집중 대상·전환 대기 조회", description = """
            ### 언제 호출하나요?
            작업 화면 진입·새로고침·앱 복귀 또는 상태 충돌 후 타이머를 복원할 때 호출합니다.

            ### 화면에 반영할 값

            - `state`: IDLE=대상 없음, RUNNING=실행 중, PAUSED=일시정지, SWITCH_WAITING=전환 대기입니다.
            - `focusTodoId`와 `latestSessionId`는 같은 집중 대상의 정보입니다. 다른 투두의 시작·재개 요청에 섞어 보내지 않습니다.
            - `closedDurationSeconds`는 저장된 시간, `currentSessionElapsedSeconds`는 현재 실행 구간의 경과시간입니다.
            - `pendingSwitch`로 대기 중인 전환을 복원하고 `serverTime`을 시간 표시 기준으로 사용합니다.
            - 대상이 없으면 200과 IDLE·null 식별자를 반환합니다. 자정이 지나도 실행 중인 세션은 유지됩니다.
            """)
    @ApiResponse(responseCode = "200", description = "현재 집중 대상·실행·전환 대기 조회", content = @Content(schema = @Schema(implementation = TimerStateResponse.class)))
    public TimerStateResponse current(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return service.current(id(jwt), stamp(jwt));
    }

    @PostMapping(value = "/timer/switches", consumes = "application/json", produces = "application/json")
    @Operation(summary = "다른 작업으로 전환 요청", description = """
            ### 입력
            현재 집중 대상 `sourceTodoId`, 옮겨갈 `targetTodoId`, 현재 또는 마지막 종료 구간의 `sourceSessionId`를 보냅니다. 일시정지 상태에서도 전환 제한을 적용합니다.

            ### 응답에 따른 화면 처리

            - `state=WAITING`: `readyAt`까지 대기 화면을 표시하고 이후 전환 확정 API를 호출합니다.
            - `state=STARTED`: 전환 제한이 꺼져 있어 즉시 새 타이머가 시작되었습니다.
            - 실행 중이던 원래 세션은 전환(SWITCHED)으로 종료합니다. 대기 시간은 집중시간에 포함하지 않습니다.
            - 대기 시작 당시 설정으로 시간이 고정되며 서버 재시작 후에도 유지됩니다.
            - `readyAt` 이후 10분 안에 확정해야 합니다. 대기 만료만으로 원래 미완료 집중 대상이 해제되지는 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "작업 전환 요청", content = @Content(schema = @Schema(implementation = TimerSwitchResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerSwitchResponse requestSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerSwitchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"sourceTodoId\":1,\"targetTodoId\":2,\"sourceSessionId\":1}")))
            @Valid @RequestBody TimerSwitchRequest request
    ) {
        return service.requestSwitch(id(jwt), stamp(jwt), request);
    }

    @PostMapping(value = "/timer/switches/{switchId}/confirm", produces = "application/json")
    @Operation(summary = "대기 완료 후 작업 전환 확정", description = """
            ### 호출 시점
            전환 요청에서 받은 `switchId`를 경로에 넣고 서버의 `readyAt` 이후 호출합니다. 프론트 카운트다운 종료만으로 자동 전환되지 않습니다.

            ### 실패 처리
            아직 대기 중이거나 원래 작업·대상 상태가 변경되면 409입니다. 현재 타이머 상태를 다시 조회해 화면을 맞춥니다. 성공하면 새 작업 세션이 시작됩니다.
            """)
    @ApiResponse(responseCode = "200", description = "대기 완료 후 전환 확정", content = @Content(schema = @Schema(implementation = TimerSwitchResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimerSwitchResponse confirmSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "전환 요청 응답 또는 현재 타이머의 pendingSwitch에서 받은 switchId입니다.") @PathVariable UUID switchId
    ) {
        return service.confirmSwitch(id(jwt), stamp(jwt), switchId);
    }

    @DeleteMapping("/timer/switches/{switchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "작업 전환 대기 취소", description = """
            전환 요청의 `switchId`를 경로에 넣습니다. 성공은 응답 본문 없는 204입니다.

            - 취소해도 원래 작업을 자동 재개하지 않습니다. 필요하면 재개 API를 호출합니다.
            - 본인 요청의 재취소는 204, 이미 전환하여 시작한 요청은 409입니다.
            """)
    @ApiResponse(responseCode = "204", description = "취소 완료", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void cancelSwitch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "전환 요청 응답 또는 현재 타이머의 pendingSwitch에서 받은 switchId입니다.") @PathVariable UUID switchId
    ) {
        service.cancelSwitch(id(jwt), stamp(jwt), switchId);
    }

    @GetMapping(value = "/todos/{todoId}/sessions", produces = "application/json")
    @Operation(summary = "투두의 작업 세션 목록", description = """
            선택한 본인 투두의 타이머 실행 구간을 오래된 순서로 조회합니다.

            - 페이지는 `page=0`부터, 기본 `size=20`, 최대 100입니다.
            - 실행 중인 구간은 종료 시각·소요시간·종료 이유가 null입니다.
            - 시작·재개 요청에 필요한 마지막 세션 ID는 해당 투두의 가장 최근 항목을 사용합니다. 첫 페이지의 마지막 항목이 전체 최신이라는 보장은 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "Todo 집중 구간 목록", content = @Content(schema = @Schema(implementation = SessionPageResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SessionPageResponse sessions(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 투두 ID. 생성·목록·상세 응답의 id를 사용합니다.", example = "1") @PathVariable Long todoId,
            @Valid @ModelAttribute @ParameterObject SessionPageQuery query
    ) {
        requirePositiveId(todoId);
        return service.history(id(jwt), stamp(jwt), todoId, query);
    }

    @GetMapping(value = "/todos/{todoId}/completion-summary", produces = "application/json")
    @Operation(summary = "투두 완료 결과·다음 작업 후보 조회", description = """
            ### 호출 순서
            먼저 `POST /api/v1/todos/{todoId}/complete`로 완료한 뒤 이 API를 조회합니다. 이 API 자체는 완료 명령이 아닙니다.

            ### 표시할 내용
            누적 실제 집중시간·일시정지/재개/전환 횟수·마감 초과시간·예상시간 초과분과 다음 작업 후보 최대 3개를 반환합니다. 최초 시작과 완료 취소 뒤 시작은 재개 횟수에 포함하지 않습니다. 완료 취소해도 과거 세션 기록은 지우지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "완료 결과·다음 작업 후보", content = @Content(schema = @Schema(implementation = TodoCompletionSummaryResponse.class)))
    @ApiResponse(responseCode = "409", description = "상태·기준 Session 충돌 또는 전환 대기 미완료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 또는 전환 요청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoCompletionSummaryResponse completionSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 투두 ID. 생성·목록·상세 응답의 id를 사용합니다.", example = "1") @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        return service.summary(id(jwt), stamp(jwt), todoId);
    }

    private void requirePositiveId(Long todoId) {
        if (todoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo ID는 양수여야 합니다.");
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}

