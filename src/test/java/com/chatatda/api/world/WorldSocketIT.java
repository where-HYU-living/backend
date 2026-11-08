package com.chatatda.api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 진짜 소켓으로 /ws/world 와 /api/v1/invites 를 끝까지 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "world.max-players=2")
class WorldSocketIT extends IntegrationTest {

    @LocalServerPort
    int port;

    final ObjectMapper om = new ObjectMapper();

    class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> in = new LinkedBlockingQueue<>();
        volatile CloseStatus closed;
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
            in.add(om.readTree(m.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closed = status;
        }

        JsonNode next(String type) throws Exception {
            for (int i = 0; i < 20; i++) {
                JsonNode n = in.poll(2, TimeUnit.SECONDS);
                assertThat(n).as("waiting for " + type).isNotNull();
                if (type.equals(n.path("t").asText())) {
                    return n;
                }
            }
            throw new AssertionError("no " + type);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }
    }

    Client connect(String query, String origin) throws Exception {
        Client c = new Client();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        c.session = new StandardWebSocketClient()
                .execute(c, headers, URI.create("ws://localhost:" + port + "/ws/world" + query)).get(5, TimeUnit.SECONDS);
        return c;
    }

    @Test
    void twoPlayers_seeEachOther_move_chat_leave() throws Exception {
        Client a = connect("?nickname=%EB%AF%BC%EC%88%98", "http://localhost:5173");
        JsonNode wa = a.next("welcome");
        assertThat(wa.path("players")).isEmpty();

        Client b = connect("?nickname=bob", "http://localhost:5173");
        JsonNode wb = b.next("welcome");
        assertThat(wb.path("players")).hasSize(1);
        assertThat(wb.path("players").get(0).path("nickname").asText()).isEqualTo("민수");
        assertThat(a.next("join").path("player").path("nickname").asText()).isEqualTo("bob");

        b.send("{\"t\":\"move\",\"p\":[3,0,4],\"r\":1.2,\"a\":\"walk\"}");
        JsonNode state = a.next("state");
        assertThat(state.path("players").get(0).path("id").asText()).isEqualTo(wb.path("selfId").asText());
        assertThat(state.path("players").get(0).path("p").get(2).asDouble()).isEqualTo(4);

        b.send("{\"t\":\"chat\",\"m\":\"안녕\"}");
        assertThat(a.next("chat").path("m").asText()).isEqualTo("안녕");
        a.send("{\"t\":\"ping\"}");
        a.next("pong");

        b.session.close();
        assertThat(a.next("leave").path("id").asText()).isEqualTo(wb.path("selfId").asText());
        a.session.close();
    }

    @Test
    void thirdPlayer_getsWorldFull_andIsClosed() throws Exception {
        Client a = connect("?nickname=a", null);
        a.next("welcome");
        Client b = connect("?nickname=b", null);
        b.next("welcome");
        Client c = connect("?nickname=c", null);
        JsonNode err = c.next("error");
        assertThat(err.path("code").asText()).isEqualTo("WORLD_FULL");
        for (int i = 0; i < 40 && c.closed == null; i++) {
            Thread.sleep(50);
        }
        assertThat(c.closed).isNotNull();
        a.session.close();
        b.session.close();
    }

    @Test
    void invite_flow_spawnsNextToHost() throws Exception {
        Client host = connect("?nickname=host", null);
        String hostId = host.next("welcome").path("selfId").asText();
        host.send("{\"t\":\"move\",\"p\":[50,0,60],\"r\":0,\"a\":\"idle\"}");
        Thread.sleep(300); // 본인의 state 는 오지 않으므로, 서버가 위치를 반영할 시간만 준다

        MvcResult res = mvc.perform(post("/api/v1/invites").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .content("{\"playerId\":\"" + hostId + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        String url = body.path("url").asText();
        assertThat(url).startsWith("http://localhost:5173/?invite=");
        assertThat(body.path("expiresAt").asText()).isNotBlank();
        String token = url.substring(url.indexOf("invite=") + 7);

        Client guest = connect("?nickname=guest&invite=" + token, null);
        JsonNode w = guest.next("welcome");
        assertThat(w.path("invited").asBoolean()).isTrue();
        assertThat(Math.hypot(w.path("spawn").path("p").get(0).asDouble() - 50, w.path("spawn").path("p").get(2).asDouble() - 60))
                .isLessThan(2);
        host.session.close();
        guest.session.close();
    }

    @Test
    void invite_forUnknownPlayer_is404() throws Exception {
        mvc.perform(post("/api/v1/invites").contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":\"nobody\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/invites").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disallowedOrigin_isRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> connect("?nickname=evil", "http://evil.example.com"))
                .hasMessageContaining("403");
    }
}
