package com.swu.atch.reward.admin;

import java.io.Serializable;

// 비밀번호 변경·탈퇴 시 브라우저 로그인도 해제할 수 있도록 계정 상태를 함께 보관한다.
public record AdminPrincipal(Long userId, String credentialStamp) implements Serializable { }
