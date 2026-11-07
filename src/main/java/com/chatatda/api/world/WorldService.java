package com.chatatda.api.world;

import com.chatatda.api.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 공유 월드 1개. 접속자 목록·입력 검증·브로드캐스트를 맡는다 (소켓은 모른다). */
@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);

    static final int CHAT_MAX = 40;
    static final int NICKNAME_MAX = 12;
    static final String DEFAULT_NICKNAME = "손님";
    static final double COORD_LIMIT = 5_000;
    static final int MOVES_PER_SECOND = 30;
    static final long CHAT_GAP_MS = 400;
    static final long EMOTE_GAP_MS = 400;
    static final double INVITE_SPAWN_DISTANCE = 1.5;
    private static final Set<String> ACTIONS = Set.of("idle", "walk", "run");
    private static final Set<String> EMOTES = Set.of("wave", "heart", "clap");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Pub(String id, String nickname, Map<String, String> look, double[] p, double r, String a) {
    }

    private final Map<String, WorldPlayer> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> perClient = new java.util.HashMap<>(); // joinLock 안에서만
    private final Object joinLock = new Object();
    private final ObjectMapper json;
    private final WorldProperties props;
    private final Clock clock;
    private final InviteService invites;

    @Autowired
    public WorldService(ObjectMapper json, WorldProperties props, Clock clock, InviteService invites) {
        this.json = json;
        this.props = props;
        this.clock = clock;
        this.invites = invites;
    }

    public boolean contains(String playerId) {
        return players.containsKey(playerId);
    }

    public int size() {
        return players.size();
    }

    // ───────── 접속 / 퇴장 ─────────

    /** 자리가 없으면 WORLD_FULL 을 보내고 empty. 있으면 welcome 을 보내고 다른 사람에게 join 을 알린다. */
    public Optional<WorldPlayer> join(Outbox out, String rawNickname, String inviteToken, String clientKey) {
        String nickname = cleanNickname(rawNickname);
        long now = clock.millis();
        WorldPlayer me;
        boolean invited = false;
        synchronized (joinLock) {
            if (players.size() >= props.maxPlayers() || perClient.getOrDefault(clientKey, 0) >= props.maxPerClient()) {
                out.send(write(Map.of("t", "error", "code", ErrorCode.WORLD_FULL.name(),
                        "message", "지금 사람이 너무 많아요. 잠시 뒤에 다시 들어와 주세요.")));
                return Optional.empty();
            }
            WorldPlayer.Pose spawn = new WorldPlayer.Pose(0, 0, 0, 0, "idle");
            Optional<WorldPlayer> host = invites.resolve(inviteToken).map(players::get);
            if (host.isPresent()) {
                spawn = besideOf(host.get().pose());
                invited = true;
            }
            me = new WorldPlayer(UUID.randomUUID().toString().substring(0, 8), nickname, clientKey, out, spawn, now);
            players.put(me.id(), me);
            perClient.merge(clientKey, 1, Integer::sum);
        }
        List<Pub> others = new ArrayList<>();
        for (WorldPlayer p : players.values()) {
            if (p != me) {
                others.add(pub(p, true));
            }
        }
        WorldPlayer.Pose s = me.pose();
        out.send(write(Map.of("t", "welcome", "selfId", me.id(), "invited", invited,
                "spawn", Map.of("p", new double[] {s.x(), s.y(), s.z()}, "r", s.r()), "players", others)));
        broadcast(Map.of("t", "join", "player", pub(me, true)), me.id());
        log.info("world join id={} nick={} invited={} online={}", me.id(), nickname, invited, players.size());
        return Optional.of(me);
    }

    public void leave(WorldPlayer me) {
        if (players.remove(me.id(), me)) {
            synchronized (joinLock) {
                perClient.merge(me.clientKey(), -1, (a, b) -> a + b == 0 ? null : a + b);
            }
            broadcast(Map.of("t", "leave", "id", me.id()), me.id());
            log.info("world leave id={} online={}", me.id(), players.size());
        }
    }

    // ───────── 받은 메시지 ─────────

    /** 잘못된 메시지는 연결을 끊지 않고 조용히 버린다. */
    public void onMessage(WorldPlayer me, String text) {
        long now = clock.millis();
        me.touch(now);
        JsonNode m;
        try {
            m = json.readTree(text);
        } catch (JsonProcessingException e) {
            return;
        }
        if (m == null || !m.isObject()) {
            return;
        }
        switch (m.path("t").asText("")) {
            case "move" -> onMove(me, m, now);
            case "emote" -> onEmote(me, m, now);
            case "look" -> onLook(me, m);
            case "chat" -> onChat(me, m, now);
            case "ping" -> me.outbox().send("{\"t\":\"pong\"}");
            default -> { }
        }
    }

    private void onMove(WorldPlayer me, JsonNode m, long now) {
        if (now - me.moveWindowStartMs >= 1000) {
            me.moveWindowStartMs = now;
            me.moveInWindow = 0;
        }
        if (++me.moveInWindow > MOVES_PER_SECOND) {
            return;
        }
        JsonNode p = m.path("p");
        if (!p.isArray() || p.size() != 3 || !m.path("r").isNumber()) {
            return;
        }
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!p.get(i).isNumber()) {
                return;
            }
            v[i] = p.get(i).asDouble();
            if (!Double.isFinite(v[i]) || Math.abs(v[i]) > COORD_LIMIT) {
                return;
            }
        }
        double r = m.path("r").asDouble();
        String a = m.path("a").asText("idle");
        if (!Double.isFinite(r) || !ACTIONS.contains(a)) {
            return;
        }
        // 소수 2자리(1cm)·각도 3자리로 줄여 JSON 크기를 줄이고, 달라진 게 없으면 보내지 않는다
        WorldPlayer.Pose next = new WorldPlayer.Pose(q(v[0], 100), q(v[1], 100), q(v[2], 100), q(r, 1000), a);
        if (!next.equals(me.pose())) {
            me.pose(next);
        }
    }

    private void onEmote(WorldPlayer me, JsonNode m, long now) {
        String e = m.path("e").asText("");
        if (!EMOTES.contains(e) || now - me.lastEmoteMs < EMOTE_GAP_MS) {
            return;
        }
        me.lastEmoteMs = now;
        broadcast(Map.of("t", "emote", "id", me.id(), "e", e), null);
    }

    /** 서버는 외형을 해석하지 않는다. 모양만 검증해 저장하고, 다음 틱에 본인 빼고 중계한다. */
    private void onLook(WorldPlayer me, JsonNode m) {
        Map<String, String> look = cleanLook(m.path("look"));
        if (look != null) {
            me.look(look);
        }
    }

    private void onChat(WorldPlayer me, JsonNode m, long now) {
        String text = cleanChat(m.path("m").asText(""));
        if (text.isEmpty() || now - me.lastChatMs < CHAT_GAP_MS) {
            return;
        }
        me.lastChatMs = now;
        broadcast(Map.of("t", "chat", "id", me.id(), "m", text), null);
    }

    /** 소켓 수준 pong 도 살아 있다는 신호다. */
    public void onPong(WorldPlayer me) {
        me.touch(clock.millis());
    }

    // ───────── 주기 작업 ─────────

    /** 서버가 먼저 ping 을 보낸다. 백그라운드 탭에서 JS 타이머가 느려져도 브라우저가 알아서 pong 한다. */
    public void pingAll() {
        for (WorldPlayer p : players.values()) {
            try {
                p.outbox().ping();
            } catch (RuntimeException e) {
                log.debug("ping failed id={}: {}", p.id(), e.toString());
            }
        }
    }

    /** 움직인 사람들의 위치·바뀐 외형을 모아 보낸다. 각자 자기 것은 빼고 받는다. */
    public void tick() {
        long t0 = System.nanoTime();
        Map<WorldPlayer, String> moved = new LinkedHashMap<>();
        for (WorldPlayer p : players.values()) {
            if (p.consumeDirty()) {
                moved.put(p, write(pub(p, false))); // 한 사람당 한 번만 직렬화한다
            }
            if (p.consumeLookDirty() && p.look() != null) {
                broadcast(Map.of("t", "look", "id", p.id(), "look", p.look()), p.id());
            }
        }
        if (!moved.isEmpty()) {
            for (WorldPlayer to : players.values()) {
                StringBuilder sb = null;
                for (Map.Entry<WorldPlayer, String> e : moved.entrySet()) {
                    if (e.getKey() == to) {
                        continue;
                    }
                    sb = sb == null ? new StringBuilder("{\"t\":\"state\",\"players\":[") : sb.append(',');
                    sb.append(e.getValue());
                }
                if (sb != null) {
                    try {
                        to.outbox().send(sb.append("]}").toString());
                    } catch (RuntimeException e) {
                        log.debug("send failed id={}: {}", to.id(), e.toString());
                    }
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (ms > props.tickMs() / 2) {
            log.warn("world tick slow: {}ms (online={})", ms, players.size());
        }
    }

    /** ping 도 없이 오래 조용한 접속을 정리한다 (죽은 연결). */
    public void reapIdle() {
        long limit = clock.millis() - props.idleTimeout().toMillis();
        for (WorldPlayer p : List.copyOf(players.values())) {
            if (p.lastSeenMs() < limit) {
                log.info("world idle-kick id={}", p.id());
                leave(p);
                p.outbox().close();
            }
        }
    }

    // ───────── 도우미 ─────────

    private void broadcast(Object msg, String exceptId) {
        String body = write(msg);
        for (WorldPlayer p : players.values()) {
            if (!p.id().equals(exceptId)) {
                try {
                    p.outbox().send(body);
                } catch (RuntimeException e) {
                    log.debug("send failed id={}: {}", p.id(), e.toString());
                }
            }
        }
    }

    private Pub pub(WorldPlayer p, boolean withNickname) {
        WorldPlayer.Pose s = p.pose();
        return new Pub(p.id(), withNickname ? p.nickname() : null, withNickname ? p.look() : null, new double[] {s.x(), s.y(), s.z()}, s.r(), s.a());
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double q(double v, double scale) {
        return Math.round(v * scale) / scale;
    }

    private static WorldPlayer.Pose besideOf(WorldPlayer.Pose host) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        return new WorldPlayer.Pose(host.x() + Math.cos(angle) * INVITE_SPAWN_DISTANCE, host.y(),
                host.z() + Math.sin(angle) * INVITE_SPAWN_DISTANCE, host.r(), "idle");
    }

    static final Pattern JACKET = Pattern.compile("^[A-Za-z0-9-]{1,16}$");
    static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    static final String DEFAULT_JACKET = "school1";
    static final String DEFAULT_HAIR = "#2a1d16";
    static final String DEFAULT_SKIN = "#f2c9a5";

    /** 객체가 아니면 버린다(null). 필드 하나가 잘못되면 그 필드만 기본값. */
    static Map<String, String> cleanLook(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        String jacket = n.path("jacket").asText("");
        String hair = n.path("hair").asText("");
        String skin = n.path("skin").asText("");
        Map<String, String> out = new LinkedHashMap<>();
        out.put("jacket", JACKET.matcher(jacket).matches() ? jacket : DEFAULT_JACKET);
        out.put("hair", HEX.matcher(hair).matches() ? hair.toLowerCase() : DEFAULT_HAIR);
        out.put("skin", HEX.matcher(skin).matches() ? skin.toLowerCase() : DEFAULT_SKIN);
        return out;
    }

    static String cleanNickname(String raw) {
        String s = stripControl(raw);
        s = truncate(s, NICKNAME_MAX);
        return s.isEmpty() ? DEFAULT_NICKNAME : s;
    }

    static String cleanChat(String raw) {
        return truncate(stripControl(raw), CHAT_MAX);
    }

    private static String stripControl(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
    }

    private static String truncate(String s, int maxCodePoints) {
        return s.codePointCount(0, s.length()) <= maxCodePoints
                ? s : s.substring(0, s.offsetByCodePoints(0, maxCodePoints)).trim();
    }
}
