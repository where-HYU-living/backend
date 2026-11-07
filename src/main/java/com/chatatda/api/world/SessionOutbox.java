package com.chatatda.api.world;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 접속자 한 명의 전송 큐. send() 는 큐에 넣고 바로 돌아오고, 실제 소켓 쓰기는 가상 스레드 하나가 순서대로 처리한다.
 * 그래서 느린 클라이언트가 있어도 월드 틱·다른 사람 전송이 막히지 않는다.
 * 큐가 넘치면(= 읽지 못하는 연결) 그 연결만 끊는다.
 */
final class SessionOutbox implements Outbox {

    private static final Logger log = LoggerFactory.getLogger(SessionOutbox.class);
    static final int QUEUE_LIMIT = 256;

    private final WebSocketSession session;
    private final Executor executor;
    private final Queue<WebSocketMessage<?>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile boolean closing;
    private volatile boolean dead;

    SessionOutbox(WebSocketSession session, Executor executor) {
        this.session = session;
        this.executor = executor;
    }

    @Override
    public void send(String json) {
        enqueue(new TextMessage(json));
    }

    @Override
    public void ping() {
        enqueue(new PingMessage());
    }

    @Override
    public void close() {
        closing = true;
        schedule();
    }

    private void enqueue(WebSocketMessage<?> m) {
        if (dead || closing) {
            return;
        }
        if (size.incrementAndGet() > QUEUE_LIMIT) {
            size.decrementAndGet();
            log.info("ws outbox overflow, dropping slow client {}", session.getId());
            kill();
            return;
        }
        queue.add(m);
        schedule();
    }

    private void schedule() {
        if (draining.compareAndSet(false, true)) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException e) {
                draining.set(false);
                kill();
            }
        }
    }

    private void drain() {
        try {
            WebSocketMessage<?> m;
            while (!dead && (m = queue.poll()) != null) {
                size.decrementAndGet();
                session.sendMessage(m);
            }
            if (closing && !dead && queue.isEmpty()) {
                dead = true;
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException | RuntimeException e) {
            log.debug("ws send failed {}: {}", session.getId(), e.toString());
            kill();
        } finally {
            draining.set(false);
            if (!dead && (!queue.isEmpty() || closing)) {
                schedule();
            }
        }
    }

    private void kill() {
        dead = true;
        queue.clear();
        size.set(0);
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException e) {
            log.debug("ws close failed {}: {}", session.getId(), e.toString());
        }
    }
}
