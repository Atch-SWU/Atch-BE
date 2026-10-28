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
@Tag(name = "내 계정", description = "인증된 본인의 LOCAL 계정 관리")
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
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호만으로 재확인합니다. 이메일 인증 불필요. 성공 시 현재 기기를 포함한 모든 기기에서 다시 로그인해야 합니다.")
    @ApiResponse(responseCode = "204", description = "비밀번호 변경 완료. 모든 기기 재로그인")
    @ApiResponse(responseCode = "403", description = "현재 비밀번호 불일치 - CURRENT_PASSWORD_INVALID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(id(jwt), stamp(jwt), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원탈퇴", description = "현재 비밀번호 확인 후 본인 계정·Todo·Session·Snapshot·AI·반복·보상·설정을 즉시 삭제합니다. 공용 아이템 카탈로그와 다른 사용자 데이터는 유지합니다. 복구할 수 없습니다. 응답 유실 시 기존 로그인으로 결과를 확인하며, 삭제 완료 계정의 재요청은 401입니다.")
    @ApiResponse(responseCode = "204", description = "회원탈퇴 완료. 클라이언트 토큰 삭제")
    @ApiResponse(responseCode = "403", description = "현재 비밀번호 불일치 - CURRENT_PASSWORD_INVALID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "교차 소유 참조가 발견돼 안전하게 중단", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountWithdrawalRequest request) {
        service.withdraw(id(jwt), stamp(jwt), request.currentPassword());
    }

    @PatchMapping("/nickname")
    @Operation(summary = "닉네임 변경", description = "한글·영문 2~8자. 중복 닉네임과 현재 닉네임 재요청을 허용합니다. 로그인은 유지됩니다.")
    @ApiResponse(responseCode = "200", description = "닉네임 변경 완료", content = @Content(schema = @Schema(implementation = NicknameResponse.class)))
    public NicknameResponse nickname(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NicknameUpdateRequest request) {
        return new NicknameResponse(service.changeNickname(id(jwt), stamp(jwt), request.nickname()));
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}
