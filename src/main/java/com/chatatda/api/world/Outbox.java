package com.chatatda.api.world;

/** 한 접속자에게 보내는 통로. 소켓 구현과 월드 로직을 분리해 소켓 없이 테스트한다. */
public interface Outbox {

    /** 즉시 돌아온다 (실제 전송은 접속자별 큐가 따로 처리). */
    void send(String json);

    /** 소켓 수준 ping. 브라우저가 JS 타이머와 무관하게 pong 을 돌려준다. */
    void ping();

    /** 큐에 쌓인 메시지를 다 보낸 뒤 닫는다. */
    void close();
}
