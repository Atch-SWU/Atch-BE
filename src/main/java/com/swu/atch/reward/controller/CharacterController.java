package com.swu.atch.reward.controller;

import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.dto.request.CharacterItemListQuery;
import com.swu.atch.reward.dto.request.EquipCharacterItemRequest;
import com.swu.atch.reward.dto.response.CharacterResponse;
import com.swu.atch.reward.dto.response.EquippedCharacterSlotResponse;
import com.swu.atch.reward.dto.response.OwnedCharacterItemPageResponse;
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
import lombok.RequiredArgsConstructor;
import com.swu.atch.reward.service.CharacterService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// 사용자 ID·현재 인증 상태는 JWT에서 전달하고 처리는 Service에 맡긴다.
// 설계 근거: docs/history/2026-11-05-reward/development-log.md
@RestController
@RequestMapping("/api/v1/character")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "08. [프론트] 캐릭터 · 보상", description = "보유·장착·현재 수집 상태와 일별 보상 결과입니다. 정산·지급은 서버 내부 처리이며 이미 정산한 날짜는 새 아이템 추가 후에도 다시 지급하지 않습니다.")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력 누락·형식·범위 오류 또는 슬롯 카테고리 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태의 토큰", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "확정 기록·장착 슬롯·수집 집계의 모순. 기존 결과를 덮어쓰지 않습니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "일일 기록/보상 정산 준비 또는 저장소 장애. 저장·장착 성공으로 표시하지 않습니다", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class CharacterController {
    private final CharacterService service;

    @GetMapping
    @Operation(summary = "현재 캐릭터·수집 현황 조회", description = """
            본인의 캐릭터 상태를 조회합니다. userId는 보내지 않습니다.
            - slots는 COLOR·BACKGROUND·EYES·ACCESSORY 순서의 4개이며, 빈 슬롯의 item·시각은 null입니다.
            - collection은 현재 활성 아이템 기준이며 활성 0개는 수집 완료가 아닙니다.
            - 기본 모습과 수집 완료 팝업의 확인 상태는 프론트에서 관리합니다.
            """)
    @ApiResponse(responseCode = "200", description = "4개 장착 슬롯과 현재 수집 현황")
    public CharacterResponse current(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.current(user(jwt), stamp(jwt));
    }

    @GetMapping("/items")
    @Operation(summary = "보유 캐릭터 아이템 목록", description = """
            본인이 보유한 아이템만 조회합니다. grade·category는 선택 필터입니다.
            - 획득 시각 내림차순, 동률이면 보유 관계 ID 내림차순입니다. page=0부터, size 기본 20·최대 100입니다.
            - 비활성 보유 아이템도 포함하며 equipped로 장착 여부를 표시합니다.
            - 장착 요청에는 보유 목록의 item.id를 characterItemId로 복사합니다.
            """)
    @ApiResponse(responseCode = "200", description = "보유 아이템 페이지")
    public OwnedCharacterItemPageResponse items(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject CharacterItemListQuery query) {
        return service.items(user(jwt), stamp(jwt), query);
    }

    @PutMapping(value = "/equipment/{category}", consumes = "application/json")
    @Operation(summary = "카테고리 슬롯에 아이템 장착·교체", description = """
            경로 category에 장착할 슬롯, 본문 characterItemId에 보유 목록의 item.id를 보냅니다.
            - 본인 소유이고 슬롯과 카테고리가 같은 아이템만 장착합니다. 비활성 보유 아이템도 허용합니다.
            - 해당 슬롯만 교체합니다. 같은 아이템 재전송은 장착 시각을 바꾸지 않습니다.
            - 성공 응답으로 슬롯을 갱신합니다. 사용자 ID·등급·자산 경로·장착 시각은 보내지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "적용된 장착 슬롯")
    @ApiResponse(responseCode = "404", description = "없는 아이템 또는 본인이 보유하지 않은 아이템", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public EquippedCharacterSlotResponse equip(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "슬롯. COLOR=색상, BACKGROUND=배경, EYES=눈, ACCESSORY=액세서리입니다.") @PathVariable ItemCategory category,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = EquipCharacterItemRequest.class),
                            examples = @ExampleObject(name = "보유 목록에서 선택한 아이템", value = "{\"characterItemId\":12}")))
            @Valid @RequestBody EquipCharacterItemRequest request) {
        return service.equip(user(jwt), stamp(jwt), category, request);
    }

    @DeleteMapping("/equipment/{category}")
    @Operation(summary = "카테고리 슬롯 장착 해제", description = """
            해당 슬롯만 비우며 보유·지급 이력과 다른 슬롯은 유지합니다.
            이미 빈 슬롯도 204입니다. 성공 후 해당 슬롯을 기본 모습으로 표시합니다.
            """)
    @ApiResponse(responseCode = "204", description = "장착 해제 완료 또는 이미 빈 슬롯")
    public ResponseEntity<Void> unequip(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "해제할 슬롯. COLOR·BACKGROUND·EYES·ACCESSORY 중 하나입니다.") @PathVariable ItemCategory category) {
        service.unequip(user(jwt), stamp(jwt), category);
        return ResponseEntity.noContent().build();
    }

    private Long user(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
