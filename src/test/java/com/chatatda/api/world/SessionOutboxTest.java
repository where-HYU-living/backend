package com.chatatda.api.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class SessionOutboxTest {

    final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        exec.shutdownNow();
    }

    @Test
    void sends_inOrder_andClosesAfterFlush() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        List<String> got = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(i -> { got.add(((TextMessage) i.getArgument(0)).getPayload()); return null; })
                .when(session).sendMessage(any(WebSocketMessage.class));
        doAnswer(i -> { closed.countDown(); return null; }).when(session).close(any(CloseStatus.class));

        SessionOutbox out = new SessionOutbox(session, exec);
        for (int i = 0; i < 100; i++) {
            out.send("m" + i);
        }
        out.close(); // 큐에 남은 것을 다 보낸 뒤에 닫혀야 한다 (WORLD_FULL 이 사라지지 않게)
        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(got).hasSize(100);
        assertThat(got.get(0)).isEqualTo("m0");
        assertThat(got.get(99)).isEqualTo("m99");
    }

    @Test
    void slowClient_doesNotBlockCaller_andIsDroppedOnOverflow() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(i -> { release.await(5, TimeUnit.SECONDS); return null; }).when(session).sendMessage(any(WebSocketMessage.class));
        doAnswer(i -> { closed.countDown(); return null; }).when(session).close(any(CloseStatus.class));

        SessionOutbox out = new SessionOutbox(session, exec);
        long t0 = System.nanoTime();
        for (int i = 0; i < SessionOutbox.QUEUE_LIMIT + 50; i++) {
            out.send("m" + i); // 첫 전송이 막혀 있어도 즉시 돌아와야 한다
        }
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(500);
        assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue(); // 넘쳐서 이 연결만 끊김
        release.countDown();
    }

    @Test
    void afterDeath_sendIsNoop() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        doAnswer(i -> { throw new java.io.IOException("broken pipe"); }).when(session).sendMessage(any(WebSocketMessage.class));
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(i -> { closed.countDown(); return null; }).when(session).close(any(CloseStatus.class));
        SessionOutbox out = new SessionOutbox(session, exec);
        out.send("x");
        assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
        out.send("y");
        verify(session, never()).sendMessage(new TextMessage("y"));
    }
}
