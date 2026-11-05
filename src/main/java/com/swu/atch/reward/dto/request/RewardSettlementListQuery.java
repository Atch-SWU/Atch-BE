package com.swu.atch.reward.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// from<=to, 최대 366일, to<서울 기준 오늘의 관계 검증은 구현할 Service의 책임이다.
@Schema(description = "종료된 서울 기준 날짜의 보상 정산 조회 조건. 양 끝을 포함하며 최대 366일입니다.")
public record RewardSettlementListQuery(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "조회 시작 날짜(포함). 서울 기준 YYYY-MM-DD이며 to와 함께 보냅니다.", example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate from,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "조회 종료 날짜(포함). from 이상이고 서울 기준 어제 이하여야 합니다. 오늘은 최종 정산 전입니다.", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate to,
        @Min(0) @Schema(description = "페이지 번호. 0부터 시작하며 생략하면 0입니다.")
        Integer page,
        @Min(1) @Max(100) @Schema(description = "페이지당 정산 날짜 수. 기본 20, 허용 범위 1~100입니다.")
        Integer size
) {
    public RewardSettlementListQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
