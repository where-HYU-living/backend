package com.chatatda.api.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorldServiceTest {

    static class Box implements Outbox {
        final List<JsonNode> got = new ArrayList<>();
        boolean closed;
        private final ObjectMapper om = new ObjectMapper();

        @Override
        public void send(String json) {
            try {
                got.add(om.readTree(json));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        int pings;

        @Override
        public void ping() {
            pings++;
        }

        @Override
        public void close() {
            closed = true;
        }

        List<JsonNode> of(String t) {
            return got.stream().filter(n -> t.equals(n.path("t").asText())).toList();
        }

        JsonNode last(String t) {
            List<JsonNode> l = of(t);
            return l.get(l.size() - 1);
        }
    }

    MutableClock clock;
    WorldProperties props;
    InviteService invites;
    WorldService world;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        props = new WorldProperties(3, 100, Duration.ofSeconds(60), Duration.ofHours(24), 2);
        invites = new InviteService(props, clock);
        world = new WorldService(new ObjectMapper(), props, clock, invites);
    }

    WorldPlayer join(Box box, String nick) {
        return world.join(box, nick, null, "ip-" + nick).orElseThrow();
    }

    void advance(long ms) {
        clock.advance(Duration.ofMillis(ms));
    }

    @Test
    void join_sendsWelcomeWithExistingPlayers_andAnnouncesJoin() {
        Box a = new Box();
        WorldPlayer pa = join(a, "민수");
        Box b = new Box();
        WorldPlayer pb = join(b, "지은");

        JsonNode welcome = b.last("welcome");
        assertThat(welcome.path("selfId").asText()).isEqualTo(pb.id());
        assertThat(welcome.path("players")).hasSize(1);
        assertThat(welcome.path("players").get(0).path("id").asText()).isEqualTo(pa.id());
        assertThat(welcome.path("players").get(0).path("nickname").asText()).isEqualTo("민수");
        assertThat(welcome.path("spawn").path("p")).hasSize(3);

        assertThat(a.of("join")).hasSize(1);
        assertThat(a.last("join").path("player").path("nickname").asText()).isEqualTo("지은");
        assertThat(b.of("join")).isEmpty(); // 자기 자신의 join 은 받지 않는다
    }

    @Test
    void join_whenFull_sendsWorldFullAndRejects() {
        for (int i = 0; i < 3; i++) {
            join(new Box(), "p" + i);
        }
        Box late = new Box();
        assertThat(world.join(late, "늦은사람", null, "ip-x")).isEmpty();
        assertThat(late.last("error").path("code").asText()).isEqualTo("WORLD_FULL");
        assertThat(world.size()).isEqualTo(3);
    }

    @Test
    void join_samePerClientLimit_isEnforced_andFreedOnLeave() {
        Box b1 = new Box();
        WorldPlayer p1 = world.join(b1, "a", null, "1.2.3.4").orElseThrow();
        world.join(new Box(), "b", null, "1.2.3.4").orElseThrow();
        Box blocked = new Box();
        assertThat(world.join(blocked, "c", null, "1.2.3.4")).isEmpty(); // 같은 IP 3번째 (한도 2)
        assertThat(blocked.last("error").path("code").asText()).isEqualTo("WORLD_FULL");
        assertThat(world.join(new Box(), "d", null, "5.6.7.8")).isPresent(); // 다른 IP 는 된다
        world.leave(p1);
        assertThat(world.join(new Box(), "e", null, "1.2.3.4")).isPresent(); // 나간 자리만큼 다시 들어올 수 있다
    }

    @Test
    void move_isQuantized_andUnchangedPoseIsNotResent() {
        Box a = new Box();
        join(a, "a");
        WorldPlayer pb = join(new Box(), "b");
        world.onMessage(pb, "{\"t\":\"move\",\"p\":[1.23456789,0,2.0049],\"r\":0.123456789,\"a\":\"walk\"}");
        world.tick();
        JsonNode p = a.last("state").path("players").get(0);
        assertThat(p.path("p").get(0).asDouble()).isEqualTo(1.23);
        assertThat(p.path("p").get(2).asDouble()).isEqualTo(2.0);
        assertThat(p.path("r").asDouble()).isEqualTo(0.123);
        // 양자화하면 같은 값 → 다시 보내지 않는다
        world.onMessage(pb, "{\"t\":\"move\",\"p\":[1.2301,0,2.0001],\"r\":0.1229,\"a\":\"walk\"}");
        world.tick();
        assertThat(a.of("state")).hasSize(1);
    }

    @Test
    void pingAll_pingsEveryone_andPongKeepsAlive() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        world.pingAll();
        assertThat(a.pings).isEqualTo(1);
        assertThat(b.pings).isEqualTo(1);
        advance(50_000);
        world.onPong(pa);
        advance(30_000);
        world.reapIdle();
        assertThat(world.size()).isEqualTo(1);
        assertThat(a.closed).isFalse();
    }

    @Test
    void state_excludesTheRecipientsOwnMove() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        WorldPlayer pb = join(b, "b");
        world.onMessage(pa, "{\"t\":\"move\",\"p\":[1,0,1],\"r\":0,\"a\":\"walk\"}");
        world.tick();
        assertThat(a.of("state")).isEmpty(); // 혼자 움직였으면 본인은 아무것도 안 받는다
        assertThat(b.last("state").path("players")).hasSize(1);
        world.onMessage(pa, "{\"t\":\"move\",\"p\":[2,0,2],\"r\":0,\"a\":\"walk\"}");
        world.onMessage(pb, "{\"t\":\"move\",\"p\":[5,0,5],\"r\":0,\"a\":\"walk\"}");
        world.tick();
        assertThat(a.last("state").path("players")).hasSize(1);
        assertThat(a.last("state").path("players").get(0).path("id").asText()).isEqualTo(pb.id());
        assertThat(b.last("state").path("players").get(0).path("id").asText()).isEqualTo(pa.id());
    }

    @Test
    void look_isValidated_relayedToOthersOnly_andShownToNewcomers() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        world.onMessage(pa, "{\"t\":\"look\",\"look\":{\"jacket\":\"dept-is\",\"hair\":\"#AABBCC\",\"skin\":\"red\"}}");
        world.tick();
        JsonNode got = b.last("look");
        assertThat(got.path("id").asText()).isEqualTo(pa.id());
        assertThat(got.path("look").path("jacket").asText()).isEqualTo("dept-is");
        assertThat(got.path("look").path("hair").asText()).isEqualTo("#aabbcc");
        assertThat(got.path("look").path("skin").asText()).isEqualTo(WorldService.DEFAULT_SKIN); // 잘못된 값은 기본값
        assertThat(a.of("look")).isEmpty(); // 본인 제외

        world.tick(); // 안 바뀌면 다시 안 보낸다
        assertThat(b.of("look")).hasSize(1);

        Box c = new Box();
        join(c, "c");
        JsonNode inWelcome = null;
        for (JsonNode p : c.last("welcome").path("players")) { // 순서는 보장되지 않으므로 a 를 id 로 찾는다
            if (p.path("id").asText().equals(pa.id())) {
                inWelcome = p;
            }
        }
        assertThat(inWelcome).isNotNull();
        assertThat(inWelcome.path("look").path("jacket").asText()).isEqualTo("dept-is");
        assertThat(b.last("join").path("player").has("look")).isFalse(); // c 는 look 을 보낸 적 없다
    }

    @Test
    void look_garbageIsIgnored_andLatestOfRapidChangesWins() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        for (String bad : new String[] {"{\"t\":\"look\"}", "{\"t\":\"look\",\"look\":\"x\"}", "{\"t\":\"look\",\"look\":[1]}"}) {
            world.onMessage(pa, bad);
        }
        world.tick();
        assertThat(b.of("look")).isEmpty();
        // 슬라이더를 끌 때처럼 한 틱 안에 여러 번 바뀌면 마지막 값이 간다 (버려지지 않는다)
        for (String hair : new String[] {"#111111", "#222222", "#333333"}) {
            world.onMessage(pa, "{\"t\":\"look\",\"look\":{\"jacket\":\"school2\",\"hair\":\"" + hair + "\",\"skin\":\"#f2c9a5\"}}");
        }
        world.tick();
        assertThat(b.of("look")).hasSize(1);
        assertThat(b.last("look").path("look").path("hair").asText()).isEqualTo("#333333");
        // 길고 이상한 잠바 이름은 기본값
        world.onMessage(pa, "{\"t\":\"look\",\"look\":{\"jacket\":\"<script>alert(1)</script>\",\"hair\":\"#111111\",\"skin\":\"#f2c9a5\"}}");
        world.tick();
        assertThat(b.last("look").path("look").path("jacket").asText()).isEqualTo("school1");
    }

    @Test
    void leave_freesSlot_andBroadcasts() {
        Box a = new Box();
        join(a, "a");
        Box b = new Box();
        WorldPlayer pb = join(b, "b");
        join(new Box(), "c");
        world.leave(pb);
        assertThat(a.last("leave").path("id").asText()).isEqualTo(pb.id());
        assertThat(world.contains(pb.id())).isFalse();
        assertThat(world.join(new Box(), "d", null, "ip-x")).isPresent();
        world.leave(pb); // 두 번 나가도 leave 가 두 번 가지 않는다
        assertThat(a.of("leave")).hasSize(1);
    }

    @Test
    void move_isBroadcastOnTick_onlyWhenChanged() {
        Box a = new Box();
        join(a, "a");
        Box b = new Box();
        WorldPlayer pb = join(b, "b");

        world.onMessage(pb, "{\"t\":\"move\",\"p\":[1.5,0,-2],\"r\":0.5,\"a\":\"walk\"}");
        assertThat(a.of("state")).isEmpty(); // tick 전에는 안 보낸다
        world.tick();
        JsonNode state = a.last("state");
        assertThat(state.path("players")).hasSize(1);
        JsonNode p = state.path("players").get(0);
        assertThat(p.path("id").asText()).isEqualTo(pb.id());
        assertThat(p.path("p").get(0).asDouble()).isEqualTo(1.5);
        assertThat(p.path("p").get(2).asDouble()).isEqualTo(-2);
        assertThat(p.path("a").asText()).isEqualTo("walk");
        assertThat(p.has("nickname")).isFalse();

        world.tick(); // 변화 없으면 아무것도 안 보낸다
        assertThat(a.of("state")).hasSize(1);
    }

    @Test
    void move_invalidInputIsIgnored() {
        Box a = new Box();
        join(a, "a");
        WorldPlayer pb = join(new Box(), "b");
        for (String bad : new String[] {
                "{\"t\":\"move\",\"p\":[1,2],\"r\":0,\"a\":\"walk\"}",
                "{\"t\":\"move\",\"p\":[1,2,\"x\"],\"r\":0,\"a\":\"walk\"}",
                "{\"t\":\"move\",\"p\":[999999,0,0],\"r\":0,\"a\":\"walk\"}",
                "{\"t\":\"move\",\"p\":[1,0,0],\"r\":0,\"a\":\"fly\"}",
                "{\"t\":\"move\",\"p\":[1,0,0],\"a\":\"walk\"}",
                "not json", "[]", "{\"t\":\"nope\"}"}) {
            world.onMessage(pb, bad);
        }
        world.tick();
        assertThat(a.of("state")).isEmpty();
        assertThat(world.contains(pb.id())).isTrue();
    }

    @Test
    void move_isRateLimited() {
        Box a = new Box();
        join(a, "a");
        WorldPlayer pb = join(new Box(), "b");
        for (int i = 0; i < 100; i++) {
            world.onMessage(pb, "{\"t\":\"move\",\"p\":[" + i + ",0,0],\"r\":0,\"a\":\"run\"}");
        }
        world.tick();
        // 초당 30개까지만 반영 → 마지막으로 반영된 x 는 29
        assertThat(a.last("state").path("players").get(0).path("p").get(0).asDouble()).isEqualTo(WorldService.MOVES_PER_SECOND - 1);
        advance(1000);
        world.onMessage(pb, "{\"t\":\"move\",\"p\":[500,0,0],\"r\":0,\"a\":\"run\"}");
        world.tick();
        assertThat(a.last("state").path("players").get(0).path("p").get(0).asDouble()).isEqualTo(500);
    }

    @Test
    void chat_isTrimmedTo40_stripsControl_andRateLimited() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        world.onMessage(pa, "{\"t\":\"chat\",\"m\":\"" + "가".repeat(60) + "\"}");
        assertThat(b.last("chat").path("m").asText()).hasSize(40);
        assertThat(b.last("chat").path("id").asText()).isEqualTo(pa.id());
        assertThat(a.of("chat")).hasSize(1); // 보낸 사람에게도 간다 (말풍선)

        world.onMessage(pa, "{\"t\":\"chat\",\"m\":\"연타\"}"); // 400ms 안 → 무시
        assertThat(b.of("chat")).hasSize(1);
        advance(500);
        world.onMessage(pa, "{\"t\":\"chat\",\"m\":\"안\\u0000녕\\n\"}");
        assertThat(b.last("chat").path("m").asText()).isEqualTo("안녕");
        advance(500);
        world.onMessage(pa, "{\"t\":\"chat\",\"m\":\"   \"}");
        assertThat(b.of("chat")).hasSize(2);
    }

    @Test
    void emote_onlyWhitelisted() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        world.onMessage(pa, "{\"t\":\"emote\",\"e\":\"<script>\"}");
        assertThat(b.of("emote")).isEmpty();
        world.onMessage(pa, "{\"t\":\"emote\",\"e\":\"heart\"}");
        assertThat(b.last("emote").path("e").asText()).isEqualTo("heart");
        assertThat(b.last("emote").path("id").asText()).isEqualTo(pa.id());
    }

    @Test
    void ping_getsPong() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        world.onMessage(pa, "{\"t\":\"ping\"}");
        assertThat(a.of("pong")).hasSize(1);
    }

    @Test
    void nickname_isCleaned() {
        assertThat(WorldService.cleanNickname(null)).isEqualTo("손님");
        assertThat(WorldService.cleanNickname("  \n ")).isEqualTo("손님");
        assertThat(WorldService.cleanNickname("가나다라마바사아자차카타파하")).hasSize(12);
        assertThat(WorldService.cleanNickname(" 민\u0000수 ")).isEqualTo("민수");
    }

    @Test
    void invite_spawnsBesideHost() {
        Box a = new Box();
        WorldPlayer host = join(a, "host");
        world.onMessage(host, "{\"t\":\"move\",\"p\":[100,0,-40],\"r\":1,\"a\":\"idle\"}");
        String token = invites.issue(host.id());

        Box b = new Box();
        WorldPlayer guest = world.join(b, "guest", token, "ip-x").orElseThrow();
        JsonNode welcome = b.last("welcome");
        assertThat(welcome.path("invited").asBoolean()).isTrue();
        double dx = welcome.path("spawn").path("p").get(0).asDouble() - 100;
        double dz = welcome.path("spawn").path("p").get(2).asDouble() + 40;
        assertThat(Math.hypot(dx, dz)).isBetween(WorldService.INVITE_SPAWN_DISTANCE - 0.01, WorldService.INVITE_SPAWN_DISTANCE + 0.01);
        assertThat(guest.pose().x()).isEqualTo(welcome.path("spawn").path("p").get(0).asDouble());
    }

    @Test
    void invite_withUnknownTokenOrGoneHost_spawnsAtDefault() {
        Box a = new Box();
        WorldPlayer host = join(a, "host");
        String token = invites.issue(host.id());

        Box b = new Box();
        world.join(b, "x", "bogus-token", "ip-x");
        assertThat(b.last("welcome").path("invited").asBoolean()).isFalse();
        assertThat(b.last("welcome").path("spawn").path("p").get(0).asDouble()).isZero();

        world.leave(host);
        Box c = new Box();
        world.join(c, "y", token, "ip-x");
        assertThat(c.last("welcome").path("invited").asBoolean()).isFalse();
    }

    @Test
    void invite_expiresAndIsReused() {
        String t1 = invites.issue("p1");
        assertThat(invites.issue("p1")).isEqualTo(t1);
        assertThat(invites.resolve(t1)).contains("p1");
        advance(Duration.ofHours(25).toMillis());
        assertThat(invites.resolve(t1)).isEmpty();
        String t2 = invites.issue("p1");
        assertThat(t2).isNotEqualTo(t1);
        assertThat(invites.resolve(null)).isEmpty();
    }

    @Test
    void reapIdle_kicksSilentConnections_butKeepsActiveOnes() {
        Box a = new Box();
        WorldPlayer pa = join(a, "a");
        Box b = new Box();
        join(b, "b");
        advance(50_000);
        world.onMessage(pa, "{\"t\":\"ping\"}");
        advance(30_000); // b 는 80초째 조용, a 는 30초
        world.reapIdle();
        assertThat(world.size()).isEqualTo(1);
        assertThat(b.closed).isTrue();
        assertThat(a.closed).isFalse();
        assertThat(a.of("leave")).hasSize(1);
    }
}
