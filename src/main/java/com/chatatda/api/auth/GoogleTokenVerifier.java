package com.chatatda.api.auth;

/** 구글 ID 토큰(JWT)의 서명·발급자·대상(aud)·만료를 확인한다. 실패하면 ApiException. */
public interface GoogleTokenVerifier {

    GoogleIdentity verify(String idToken);
}
