package com.chatatda.api.world;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

/** /ws/world — 소켓과 WorldService 를 이어 주는 얇은 층. */
@Component
public class WorldSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WorldSocketHandler.class);
    private static final String PLAYER = "world.player";
    private static final int MAX_MESSAGE_BYTES = 2048;

    private final WorldService world;
    // 접속자별 전송 큐를 비우는 일꾼. 가상 스레드라 소켓에서 막혀도 비용이 거의 없다
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public WorldSocketHandler(WorldService world) {
        this.world = world;
    }

    @PreDestroy
    void shutdown() {
        sendExecutor.shutdownNow();
    }

    /** 리버스 프록시(Railway 등) 뒤라면 X-Forwarded-For 의 첫 값이 실제 접속자다. */
    static String clientKey(WebSocketSession s) {
        String xff = s.getHandshakeHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return s.getRemoteAddress() == null ? "unknown" : s.getRemoteAddress().getAddress().getHostAddress();
    }

    /** 응답 없는 연결에 쓰기가 영원히 걸려 있지 않게 5초로 끊는다 (Tomcat 기본 20초). */
    private static void limitBlockingSend(WebSocketSession session) {
        try {
            if (session instanceof StandardWebSocketSession std && std.getNativeSession() != null) {
                std.getNativeSession().getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 5_000L);
            }
        } catch (RuntimeException e) {
            log.debug("could not set send timeout: {}", e.toString());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        limitBlockingSend(session);
        Outbox out = new SessionOutbox(session, sendExecutor);
        var params = UriComponentsBuilder.fromUri(uriOf(session)).build().getQueryParams();
        String nickname = decode(params.getFirst("nickname"));
        String invite = decode(params.getFirst("invite"));
        var joined = world.join(out, nickname, invite, clientKey(session));
        if (joined.isPresent()) {
            session.getAttributes().put(PLAYER, joined.get());
        } else {
            out.close(); // WORLD_FULL 은 큐에 이미 들어 있다. 다 보낸 뒤 닫힌다. 클라이언트는 이 경우 재접속하지 않는다
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        if (session.getAttributes().get(PLAYER) instanceof WorldPlayer me) {
            world.onPong(me);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (session.getAttributes().get(PLAYER) instanceof WorldPlayer me) {
            world.onMessage(me, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getAttributes().get(PLAYER) instanceof WorldPlayer me) {
            world.leave(me);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("ws transport error: {}", exception.toString());
        if (session.getAttributes().get(PLAYER) instanceof WorldPlayer me) {
            world.leave(me);
        }
    }

    private static URI uriOf(WebSocketSession s) {
        return s.getUri() == null ? URI.create("/") : s.getUri();
    }

    private static String decode(String v) {
        return v == null ? null : UriUtils.decode(v, StandardCharsets.UTF_8);
    }
}
