package com.chatatda.api.auth;

import java.security.PublicKey;
import java.util.Optional;

/** 구글이 ID 토큰 서명에 쓰는 공개키(kid 로 구분). */
public interface GoogleKeySource {

    Optional<PublicKey> find(String keyId);
}
