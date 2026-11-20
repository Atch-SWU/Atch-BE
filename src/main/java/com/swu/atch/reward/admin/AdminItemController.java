package com.swu.atch.reward.admin;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.common.exception.ErrorResponse;
import com.swu.atch.reward.repository.CharacterItemRepository;
import com.swu.atch.reward.service.RewardResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/character-items")
@RequiredArgsConstructor
@Tag(name = "09. [운영진] 아이템 관리", description = "운영진 등록 화면에서 호출합니다. /admin/login에서 운영진 계정으로 로그인한 브라우저 세션과 쓰기 요청의 CSRF 토큰이 필요합니다.")
@SecurityScheme(name = "adminSession", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "ATCH_ADMIN_SESSION")
@SecurityRequirement(name = "adminSession")
@ApiResponse(responseCode = "401", description = "운영진 로그인 세션이 없거나 만료됨", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "운영진 권한이 없거나 CSRF 토큰이 누락·만료됨", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AdminItemController {
    private final AdminItemRegistrationService registration;
    private final CharacterItemRepository items;
    private final RewardResponses responses;

    @GetMapping
    @Operation(summary = "[운영진 화면] 등록된 아이템 목록", description = "전체 카탈로그를 최근 등록순으로 조회합니다. 일반 앱의 보유 목록은 GET /api/v1/character/items를 사용합니다.")
    public AdminItemPageResponse list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var result = items.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new AdminItemPageResponse(result.stream()
                .map(item -> new AdminCatalogItemResponse(item.getItemCode(), responses.item(item))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "[운영진 화면] 이미지·아이템 일괄 등록", description = """
            multipart/form-data의 metadata에는 application/json 파일 파트, files에는 이미지 파일 목록을 보냅니다.
            - metadata 예시 {"items":[{"itemCode":"chef-hat","name":"요리사 모자","grade":"A","category":"ACCESSORY","fileIndex":0}]}
            - PNG·JPEG만 지원하며 한 장당 2MB, 가로·세로 각각 4096px, 한 묶음 1~20개·파일 합계 20MB입니다.
            - 같은 itemCode와 같은 이름·등급·카테고리·이미지를 다시 보내면 기존 결과를 반환합니다.
            - 같은 코드를 다른 내용으로 보내면 409입니다. 묶음의 DB 저장은 함께 성공하거나 함께 취소합니다.
            - 이미지 업로드 후 DB 저장을 완료합니다. 장애 후에는 새 코드를 만들지 않고 같은 항목으로 재시도합니다.
            - /admin/csrf 응답의 token을 X-CSRF-TOKEN 헤더에 넣습니다. 운영진 화면은 이를 자동 처리합니다.
            """)
    @ApiResponse(responseCode = "200", description = "입력 순서의 등록 결과 또는 동일 요청의 기존 결과")
    @ApiResponse(responseCode = "400", description = "입력값·파일 수·이미지 형식·크기 오류")
    @ApiResponse(responseCode = "409", description = "같은 등록 코드가 다른 내용으로 사용 중")
    @ApiResponse(responseCode = "413", description = "multipart 요청 용량 초과")
    @ApiResponse(responseCode = "503", description = "이미지 저장소 또는 DB 장애. 같은 항목으로 재시도")
    public AdminItemBatchResponse register(@Parameter(hidden = true) @AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader(name = "X-CSRF-TOKEN", required = false) @Parameter(description = "/admin/csrf에서 받은 CSRF token") String csrf,
            @Valid @RequestPart("metadata") AdminItemBatchRequest metadata,
            @RequestPart("files") List<MultipartFile> files) {
        return registration.register(principal, metadata, files);
    }
}
