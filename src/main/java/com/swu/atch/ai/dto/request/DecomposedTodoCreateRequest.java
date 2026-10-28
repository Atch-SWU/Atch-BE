package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.dto.request.EstimateChoiceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// 최종 승인한 세부 Todo 일괄 생성. 원본 부모는 생성하지 않음
@Schema(description = "최종 승인한 세부 Todo 일괄 생성. 원본 부모는 생성하지 않음")
public record DecomposedTodoCreateRequest(
        @NotNull @Schema(description = "중복 저장 방지용 요청 UUID. 프론트가 한 번의 저장 동작에 대해 생성합니다. 응답 유실 후 같은 본문으로 재전송할 때 같은 값을 유지합니다.", requiredMode = Schema.RequiredMode.REQUIRED) UUID creationRequestId,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "분해한 원본 작업의 초안 UUID. 분해 응답에서 받은 값을 그대로 보냅니다. 저장된 부모 투두 ID가 아닙니다.") UUID decompositionDraftId,
        @NotBlank @Size(max = 200) @Schema(description = "원본 태스크명(필수). 분해 전 원본 작업 이름이며 생성되는 세부 투두에 함께 저장합니다(최대 200자).", example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String sourceTaskName,
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).")
        @Size(max = 2000) String memo,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "시작 날짜. 서울 기준 YYYY-MM-DD 형식입니다. 신규 생성은 오늘 이후 날짜를 입력합니다.") LocalDate startDate,
        @Schema(example = "18:00:00", type = "string", format = "time", description = "시작 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 true이면 null을 보냅니다.") LocalTime startTime,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "마감 날짜. YYYY-MM-DD 형식입니다. 하루종일 일정은 시작 날짜와 같은 날짜를 보냅니다.") LocalDate dueDate,
        @Schema(example = "20:00:00", type = "string", format = "time", description = "마감 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 마감 일시는 시작 일시보다 뒤여야 합니다. true이면 null을 보냅니다.") LocalTime dueTime,
        @NotNull @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED, description = "하루종일 일정 여부. true이면 시작·마감 날짜를 같은 날로 지정하고 두 시각은 null로 보냅니다. 서버가 해당 서울 날짜 하루로 처리합니다.") Boolean allDay,
        @NotNull @Size(min = 1, max = 20) @Valid
        @Schema(description = "세부 작업 배열(1~20개). 각 작업의 서버 발급 draftId·편집한 태스크명·사용자 직접 입력시간을 담습니다. 순서는 화면에 표시할 순서입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull DecomposedTaskInput> tasks,
        @Valid @Schema(description = "묶음 전체의 예상시간 선택(선택). 생략하면 USER입니다. draftId에는 원본 decompositionDraftId를 넣습니다. AI 선택 시 묶음 예측 응답의 predictionToken이 필수입니다.") EstimateChoiceRequest estimateChoice
) {

}

