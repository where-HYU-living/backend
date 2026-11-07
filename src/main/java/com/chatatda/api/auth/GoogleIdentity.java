package com.chatatda.api.auth;

/** 검증을 통과한 구글 ID 토큰에서 꺼낸 값. */
public record GoogleIdentity(String email, boolean emailVerified, String hostedDomain) {
}
