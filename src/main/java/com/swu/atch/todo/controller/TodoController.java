package com.swu.atch.todo.controller;

import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.todo.dto.request.TodoCreateRequest;
import com.swu.atch.todo.dto.request.TodoDateRangeQuery;
import com.swu.atch.todo.dto.request.TodoListQuery;
import com.swu.atch.todo.dto.request.TodoUpdateRequest;
import com.swu.atch.todo.dto.response.TodoCalendarResponse;
import com.swu.atch.todo.dto.response.TodoPageResponse;
import com.swu.atch.todo.dto.response.TodoResponse;
import com.swu.atch.todo.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

// FR-TODO-001·002·004~016 및 이월 데이터의 조회·보존. 자동 이월을 연결하며 AI 분해는 별도 단계다.
// 사용자 ID와 비밀번호 상태는 요청 본문 대신 검증된 JWT에서 가져온다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/todos")
@Tag(name = "TODO", description = "본인 Todo 생성·일정 조회·수정·논리 삭제·완료 및 완료 취소")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력 형식·필수값·일정·조회 조건 오류 - VALIDATION_FAILED, INVALID_REQUEST, BAD_REQUEST",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "미인증·삭제 계정·이전 비밀번호 상태의 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TodoController {
    private final TodoService service;
    private static final String TIMED_EXAMPLE = """
            {"title":"TODO API 설계","memo":null,"startDate":"2026-10-29","startTime":"09:00:00",
             "dueDate":"2026-10-29","dueTime":"10:00:00","allDay":false,"userEstimatedDurationMinutes":30}
            """;
    private static final String ALL_DAY_EXAMPLE = """
            {"title":"발표 준비","memo":null,"startDate":"2026-10-29","startTime":null,
             "dueDate":"2026-10-29","dueTime":null,"allDay":true,"userEstimatedDurationMinutes":30}
            """;

    // 날짜·시각은 서울 현지 입력으로 받고, 저장·응답 시각은 Instant로 변환한다.
    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Todo 생성", description = """
            일반 Todo를 USER_INPUT·USER·PENDING으로 생성하고 currentScheduleDate=startDate로 배정합니다.
            신규 시작 날짜는 서울 오늘 이후이며 오늘의 지난 시각은 허용합니다. 시각 지정은 마감>시작이어야 합니다. 미완료 Todo의 마감 초과는 마감에 도달한 시각부터 판정합니다.
            allDay=true는 같은 시작/마감 날짜와 null 시각을 입력하며 서울 00:00부터 다음 날 00:00 미만입니다.
            userId·상태·반복 식별값·AI 예상값은 입력하지 않습니다. AI 제안 토큰 연동은 후속 AI 단계입니다.
            생성은 멱등하지 않습니다. 응답 유실 시 목록을 확인한 뒤 재시도합니다.
            """)
    @ApiResponse(responseCode = "201", description = "생성된 Todo",
            headers = @Header(name = "Location", description = "생성 리소스 /api/v1/todos/{todoId}", schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = TodoResponse.class)))
    public ResponseEntity<TodoResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TodoCreateRequest.class),
                            examples = {@ExampleObject(name = "시각 지정", value = TIMED_EXAMPLE), @ExampleObject(name = "하루종일", value = ALL_DAY_EXAMPLE)}))
            @Valid @RequestBody TodoCreateRequest request
    ) {
        TodoResponse result = service.create(id(jwt), stamp(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/todos/" + result.id())).body(result);
    }

    // 상태 필터를 먼저 적용하고 페이지를 나눈다. 페이지를 읽은 뒤 필터링하면 개수·다음 페이지가 틀어진다.
    @GetMapping(produces = "application/json")
    @Operation(summary = "날짜·기간·상태별 Todo 목록", description = """
            본인의 삭제되지 않은 Todo를 currentScheduleDate 기준으로 조회합니다. 과거 날짜 종료 상태 조회가 아닙니다.
            date 또는 from/to(양 끝 포함)를 사용하고 함께 입력할 수 없습니다. from/to는 둘 다 필요합니다.
            날짜 조건 생략 시 서울 오늘, status 생략 시 전체 상태입니다. 복수 상태는 쉼표로 연결합니다.
            날짜·상태 필터 후 currentScheduleDate,startAt,id 오름차순으로 페이지를 나눕니다.
            page는 0부터 시작하는 묶음 번호, size는 한 묶음 최대 개수(기본 20, 최대 100)입니다.
            """)
    @ApiResponse(responseCode = "200", description = "필터된 목록과 페이지 정보",
            content = @Content(schema = @Schema(implementation = TodoPageResponse.class)))
    public TodoPageResponse list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject TodoListQuery query
    ) {
        return service.list(id(jwt), query);
    }

    // 캘린더 점 표시는 현재 배정 날짜 기준이며 삭제된 Todo와 과거 Snapshot은 포함하지 않는다.
    @GetMapping(value = "/calendar", produces = "application/json")
    @Operation(summary = "캘린더 Todo 존재 날짜", description = """
            from/to 범위(양 끝 포함)의 현재 배정 날짜 중 본인 Todo가 존재하는 날짜를 중복 없이 오름차순 반환합니다.
            완료는 포함하고 논리 삭제는 제외합니다. from<=to여야 합니다.
            밀린 일일 확정·이월 후 현재 배정 날짜를 조회합니다. 원래 시작~마감 구간의 겹침이나 과거 확정 통계가 아닙니다.
            """)
    @ApiResponse(responseCode = "200", description = "Todo가 있는 날짜 목록",
            content = @Content(schema = @Schema(implementation = TodoCalendarResponse.class)))
    public TodoCalendarResponse calendar(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject TodoDateRangeQuery query
    ) {
        return service.calendar(id(jwt), query);
    }

    @GetMapping(value = "/{todoId}", produces = "application/json")
    @Operation(summary = "Todo 상세", description = "본인 소유의 활성 Todo만 반환합니다. 없는 ID·타인 소유·삭제된 Todo는 동일한 404입니다. 실제시간은 종료된 Session의 초 합계입니다.")
    @ApiResponse(responseCode = "200", description = "Todo 상세", content = @Content(schema = @Schema(implementation = TodoResponse.class)))
    @ApiResponse(responseCode = "404", description = "조회 가능한 Todo 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoResponse detail(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1", schema = @Schema(minimum = "1")) @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        return service.detail(id(jwt), todoId);
    }

    // 완료 취소·일시정지 없이 정보를 덮어쓰지 않는다. AI 선택 무효화도 Service 트랜잭션에서 처리한다.
    @PutMapping(value = "/{todoId}", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Todo 편집 폼 전체 수정", description = """
            수정 가능한 폼 전체를 전송합니다. memo=null은 기존 메모 삭제입니다.
            실행 중이면 일시정지 후, 완료 상태이면 완료 취소 후 수정합니다(충돌 409).
            제목·메모만 수정하면 이월된 배정 날짜를 유지하고, 시작 날짜를 실제 변경할 때만 재배정합니다. 과거 날짜로 수정해도 현재 배정 날짜는 서울 오늘보다 과거로 되돌리지 않습니다.
            반복 규칙·불변 회차 식별값은 유지합니다. 사용자 예상시간을 직접 변경하면 출처를 USER_INPUT으로 바꿉니다.
            예측 입력(작업명·메모·사용자 예상시간)이 변경되면 AI 예상값·선택을 무효화하고 사용자 예상시간을 사용합니다.
            기존 Session·Snapshot·실험 이력은 보존합니다. AI·반복 API 자체는 별도 단계입니다.
            """)
    @ApiResponse(responseCode = "200", description = "수정된 Todo", content = @Content(schema = @Schema(implementation = TodoResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "실행 중 또는 완료 상태 - TODO_PAUSE_REQUIRED, TODO_REOPEN_REQUIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1", schema = @Schema(minimum = "1")) @PathVariable Long todoId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TodoUpdateRequest.class),
                            examples = {@ExampleObject(name = "시각 지정", value = TIMED_EXAMPLE), @ExampleObject(name = "하루종일", value = ALL_DAY_EXAMPLE)}))
            @Valid @RequestBody TodoUpdateRequest request
    ) {
        requirePositiveId(todoId);
        return service.update(id(jwt), stamp(jwt), todoId, request);
    }

    // 논리 삭제는 현재 조회에서만 제외하며 과거 Snapshot·집중 기록·실험을 지우지 않는다.
    @DeleteMapping("/{todoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Todo 논리 삭제", description = """
            본인 Todo의 deletedAt만 기록합니다. 실행 중이면 먼저 일시정지해야 합니다(409).
            완료 Todo는 완료 취소 없이 삭제할 수 있습니다. Session·Snapshot·실험·반복 회차 중복 방지는 유지합니다.
            본인의 이미 삭제된 Todo 재요청은 204, 타인 소유·존재하지 않는 ID는 404입니다.
            """)
    @ApiResponse(responseCode = "204", description = "삭제 완료 또는 본인 Todo 이미 삭제됨", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "404", description = "본인 Todo 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "실행 중. 일시정지 필요 - TODO_PAUSE_REQUIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1", schema = @Schema(minimum = "1")) @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        service.delete(id(jwt), stamp(jwt), todoId);
    }

    // 실행 중 완료 시 열린 Session 종료와 Todo 완료를 같은 트랜잭션으로 처리한다.
    @PostMapping(value = "/{todoId}/complete", produces = "application/json")
    @Operation(summary = "Todo 완료", description = """
            PENDING·PAUSED·IN_PROGRESS에서 완료할 수 있습니다. 서버 시각으로 완료하고 열린 Session이 있으면 함께 종료합니다.
            이미 완료된 Todo 재요청은 최초 완료 시각과 실제시간을 유지합니다. 타이머 실행 중 완료 시 같은 트랜잭션으로 구간을 종료합니다.
            """)
    @ApiResponse(responseCode = "200", description = "완료된 Todo", content = @Content(schema = @Schema(implementation = TodoResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Session·완료 상태 또는 시작 시각 불일치 - RESOURCE_CONFLICT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoResponse complete(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1", schema = @Schema(minimum = "1")) @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        return service.complete(id(jwt), stamp(jwt), todoId);
    }

    // 완료 취소는 현재 상태만 되돌린다. 과거 날짜 기록이나 실제 집중시간을 소급 삭제하지 않는다.
    @PostMapping(value = "/{todoId}/reopen", produces = "application/json")
    @Operation(summary = "Todo 완료 취소", description = """
            COMPLETED를 PENDING으로 바꾸고 completedAt을 비웁니다. Session·과거 Snapshot·실험을 유지하고 과거 배정 날짜는 서울 오늘로 옮깁니다.
            이미 미완료 상태이면 현재 상태를 그대로 반환하며 실행 중인 Todo를 초기화하지 않습니다.
            오늘 이후 배정 날짜는 유지하며 타이머를 자동 재시작하지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "현재 Todo", content = @Content(schema = @Schema(implementation = TodoResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 활성 Todo 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Session·완료 상태 또는 시작 시각 불일치 - RESOURCE_CONFLICT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TodoResponse reopen(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "본인 Todo ID", example = "1", schema = @Schema(minimum = "1")) @PathVariable Long todoId
    ) {
        requirePositiveId(todoId);
        return service.reopen(id(jwt), stamp(jwt), todoId);
    }

    private void requirePositiveId(Long todoId) {
        // 경로 입력을 별도로 검사해 본문 @Valid의 기존 필드 오류 응답을 유지한다.
        if (todoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todo ID는 양수여야 합니다.");
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
