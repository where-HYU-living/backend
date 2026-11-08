package com.chatatda.api.world;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldPlayer {

    /**
     * room: 실내 방 투어 중이면 그 도면 id, 바깥(캠퍼스·동네)이면 null.
     * 실내일 때 x/y/z 는 그 도면의 좌표(m)라 바깥 좌표와 섞으면 안 된다 — 받는 쪽이 room 으로 나눠 그린다.
     */
    public record Pose(double x, double y, double z, double r, String a, Integer room) {
        public Pose(double x, double y, double z, double r, String a) {
            this(x, y, z, r, a, null);
        }
    }

    private final String id;
    private final String nickname;
    private final String clientKey;
    private final Outbox outbox;
    private volatile Pose pose;
    private volatile long lastSeenMs;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean lookDirty = new AtomicBoolean();
    /** 검증된 외형 (jacket/hair/skin). 아직 안 보냈으면 null → 받는 쪽이 기본 외형으로 그린다 */
    private volatile Map<String, String> look;

    // 스팸 방지용 (접속 하나당 한 스레드가 읽고 쓰므로 volatile 이면 충분)
    volatile long moveWindowStartMs;
    volatile int moveInWindow;
    volatile long lastChatMs;
    volatile long lastEmoteMs;

    WorldPlayer(String id, String nickname, String clientKey, Outbox outbox, Pose pose, long nowMs) {
        this.id = id;
        this.clientKey = clientKey;
        this.nickname = nickname;
        this.outbox = outbox;
        this.pose = pose;
        this.lastSeenMs = nowMs;
    }

    public String id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    String clientKey() {
        return clientKey;
    }

    Outbox outbox() {
        return outbox;
    }

    public Pose pose() {
        return pose;
    }

    void pose(Pose pose) {
        this.pose = pose;
        dirty.set(true);
    }

    long lastSeenMs() {
        return lastSeenMs;
    }

    void touch(long nowMs) {
        this.lastSeenMs = nowMs;
    }

    public Map<String, String> look() {
        return look;
    }

    void look(Map<String, String> look) {
        this.look = look;
        lookDirty.set(true);
    }

    boolean consumeLookDirty() {
        return lookDirty.getAndSet(false);
    }

    boolean consumeDirty() {
        return dirty.getAndSet(false);
    }
}
