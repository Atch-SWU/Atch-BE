package com.swu.atch.user.controller;

import com.swu.atch.user.dto.request.*;
import com.swu.atch.user.dto.response.NicknameResponse;
import com.swu.atch.user.service.UserAccountService;
import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
@Tag(name = "02. [프론트] 내 계정")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력값 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "미인증·삭제 계정·이전 비밀번호 상태의 토큰", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class UserAccountController {
    private final UserAccountService service;

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "비밀번호 변경", description = """
            현재 비밀번호와 새 비밀번호를 보냅니다. 로그인한 본인 계정만 변경하며 이메일 인증은 필요하지 않습니다.

            성공하면 현재 기기를 포함한 모든 기기에서 다시 로그인해야 합니다. 프론트의 인증 상태를 정리하고 로그인 화면으로 이동합니다.
            """)
    @ApiResponse(responseCode = "204", description = "비밀번호 변경 완료. 모든 기기 재로그인")
    @ApiResponse(responseCode = "403", description = "현재 비밀번호 불일치 - CURRENT_PASSWORD_INVALID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(id(jwt), stamp(jwt), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원탈퇴", description = """
            ### 입력
            현재 비밀번호로 본인을 확인합니다. 프론트에서 탈퇴 확인을 마친 뒤 호출합니다.

            ### 처리 결과
            본인 계정과 투두·작업 세션·일별 기록·AI·반복·보상·설정을 즉시 삭제하며 복구할 수 없습니다. 공용 아이템과 타인 데이터는 유지합니다.

            성공하면 인증 상태를 정리합니다. 응답 유실 시 기존 계정으로 로그인해 결과를 확인하며, 삭제 완료 계정의 재요청은 401입니다.
            """)
    @ApiResponse(responseCode = "204", description = "회원탈퇴 완료. 클라이언트 토큰 삭제")
    @ApiResponse(responseCode = "403", description = "현재 비밀번호 불일치 - CURRENT_PASSWORD_INVALID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "교차 소유 참조가 발견돼 안전하게 중단", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountWithdrawalRequest request) {
        service.withdraw(id(jwt), stamp(jwt), request.currentPassword());
    }

    @PatchMapping("/nickname")
    @Operation(summary = "닉네임 변경", description = """
            한글·영문 2~8자의 새 닉네임을 보냅니다. 중복 닉네임과 현재 닉네임 재요청을 허용합니다.

            성공하면 응답의 닉네임으로 화면을 갱신합니다. 로그인 상태는 유지됩니다.
            """)
    @ApiResponse(responseCode = "200", description = "닉네임 변경 완료", content = @Content(schema = @Schema(implementation = NicknameResponse.class)))
    public NicknameResponse nickname(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NicknameUpdateRequest request) {
        return new NicknameResponse(service.changeNickname(id(jwt), stamp(jwt), request.nickname()));
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
