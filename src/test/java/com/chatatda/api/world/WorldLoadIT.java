package com.chatatda.api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 정원(30명)이 전부 10Hz 로 움직이는 상황: 모두가 모두의 움직임을 받고, 31번째는 거절된다. 개인 IP 제한은 이 테스트에서 푼다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"world.max-players=30", "world.max-per-client=100"})
class WorldLoadIT extends IntegrationTest {

    @LocalServerPort
    int port;

    static final int N = 30;
    static final int SECONDS = 4;

    static class Peer extends TextWebSocketHandler {
        final ObjectMapper om = new ObjectMapper();
        final int index;
        volatile String selfId;
        final Set<String> seenMovers = ConcurrentHashMap.newKeySet();
        final AtomicInteger states = new AtomicInteger();
        final AtomicLong maxBytes = new AtomicLong();
        final CountDownLatch welcomed = new CountDownLatch(1);
        volatile boolean closedByServer;
        WebSocketSession session;

        Peer(int index) {
            this.index = index;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
            maxBytes.accumulateAndGet(m.getPayloadLength(), Math::max);
            JsonNode n = om.readTree(m.getPayload());
            switch (n.path("t").asText()) {
                case "welcome" -> { selfId = n.path("selfId").asText(); welcomed.countDown(); }
                case "state" -> {
                    states.incrementAndGet();
                    for (JsonNode p : n.path("players")) {
                        if (!p.path("id").asText().equals(selfId)) {
                            seenMovers.add(p.path("id").asText());
                        }
                    }
                }
                default -> { }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closedByServer = true;
        }
    }

    @Test
    void thirtyPlayers_allMoving_allSeeEachOther_andThe31stIsRejected() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        List<Peer> peers = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Peer p = new Peer(i);
            p.session = client.execute(p, null, URI.create("ws://localhost:" + port + "/ws/world?nickname=p" + i)).get(5, TimeUnit.SECONDS);
            assertThat(p.welcomed.await(3, TimeUnit.SECONDS)).as("welcome " + i).isTrue();
            peers.add(p);
        }

        // 31번째는 거절
        Peer extra = new Peer(99);
        extra.session = client.execute(extra, null, URI.create("ws://localhost:" + port + "/ws/world?nickname=extra")).get(5, TimeUnit.SECONDS);
        for (int i = 0; i < 40 && !extra.closedByServer; i++) {
            Thread.sleep(50);
        }
        assertThat(extra.closedByServer).isTrue();
        assertThat(extra.selfId).isNull();

        // 테스트의 시계는 고정돼 있으므로(속도 제한 창이 안 열린다) 실제 시간만큼 흘려 준다
        Thread ticker = Thread.ofVirtual().start(() -> {
            try {
                while (true) {
                    Thread.sleep(50);
                    clock.advance(java.time.Duration.ofMillis(50));
                }
            } catch (InterruptedException e) {
                // 테스트 끝
            }
        });

        // 전원이 10Hz 로 움직인다
        Thread[] senders = new Thread[N];
        for (int i = 0; i < N; i++) {
            final Peer p = peers.get(i);
            senders[i] = Thread.ofVirtual().start(() -> {
                try {
                    for (int t = 0; t < SECONDS * 10; t++) {
                        p.session.sendMessage(new TextMessage("{\"t\":\"move\",\"p\":[" + (p.index + t * 0.5) + ",0," + t + "],\"r\":0.5,\"a\":\"walk\"}"));
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        for (Thread t : senders) {
            t.join();
        }
        Thread.sleep(500);

        Set<String> all = new HashSet<>();
        for (Peer p : peers) {
            all.add(p.selfId);
        }
        assertThat(all).hasSize(N);
        for (Peer p : peers) {
            // 자기를 뺀 29명 전부의 움직임을 받았다
            assertThat(p.seenMovers).as("peer " + p.index).hasSize(N - 1);
            // 틱 수가 대략 10Hz 로 유지됐다 (4초 → 30 이상)
            assertThat(p.states.get()).as("states of peer " + p.index).isGreaterThan(SECONDS * 10 * 3 / 4);
            // 메시지 한 개가 작아야 한다 (30명 × 약 70바이트)
            assertThat(p.maxBytes.get()).isLessThan(4096);
        }
        ticker.interrupt();
        for (Peer p : peers) {
            p.session.close();
        }
    }
}
